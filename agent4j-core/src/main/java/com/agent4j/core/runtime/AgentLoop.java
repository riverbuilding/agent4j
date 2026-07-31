package com.agent4j.core.runtime;

import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiModelClient;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiContentBlocks;
import com.agent4j.ai.AiSystemMessage;
import com.agent4j.ai.AiToolSpec;
import com.agent4j.ai.AiTurnRequest;
import com.agent4j.ai.AiUsage;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.AssistantAgentMessageView;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.message.ToolResultAgentMessageView;
import com.agent4j.core.tool.ToolContext;
import com.agent4j.core.tool.ToolExecutor;
import com.agent4j.core.tool.ToolExecutionHook;
import com.agent4j.core.tool.ToolRegistry;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class AgentLoop {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final AiModelClient modelClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final AgentEventBus eventBus;
    private final AgentMessageConverter messageConverter;
    private final List<ToolExecutionHook> toolExecutionHooks;

    public AgentLoop(AiModelClient modelClient, ToolRegistry toolRegistry, AgentEventBus eventBus) {
        this(modelClient, toolRegistry, eventBus, DefaultAgentMessageConverter.INSTANCE);
    }

    public AgentLoop(
            AiModelClient modelClient,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus,
            AgentMessageConverter messageConverter
    ) {
        this(modelClient, toolRegistry, eventBus, messageConverter, List.of());
    }

    public AgentLoop(
            AiModelClient modelClient,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus,
            AgentMessageConverter messageConverter,
            List<ToolExecutionHook> toolExecutionHooks
    ) {
        this.modelClient = Objects.requireNonNull(modelClient, "modelClient");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolExecutor = new ToolExecutor(toolRegistry);
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.messageConverter = Objects.requireNonNull(messageConverter, "messageConverter");
        this.toolExecutionHooks = toolExecutionHooks == null ? List.of() : List.copyOf(toolExecutionHooks);
    }

    public AgentLoop(
            AiModelClient modelClient,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus,
            List<ToolExecutionHook> toolExecutionHooks
    ) {
        this(modelClient, toolRegistry, eventBus, DefaultAgentMessageConverter.INSTANCE, toolExecutionHooks);
    }

    public AgentLoopResult runTurn(AgentLoopRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        request.abortSignal().throwIfAborted();
        eventBus.publish(new AgentEvent.AgentStarted(request.sessionId(), now(request), request.turnId()));

        List<AgentMessage> assistantMessages = new ArrayList<>();
        List<AiMessage> modelMessages = initialModelMessages(request);
        List<AgentMessage> newMessages = new ArrayList<>(request.promptMessages());
        List<ToolResult> toolResults = new ArrayList<>();
        List<AgentMessage> steeringQueue = new ArrayList<>(request.steeringMessages());
        List<AgentMessage> followUpQueue = new ArrayList<>(request.followUpMessages());
        List<AgentMessage> pendingMessages = new ArrayList<>();
        Usage usage = Usage.zero();

        try {
            for (int round = 0; round <= request.maxToolRounds(); round++) {
                request.abortSignal().throwIfAborted();
                eventBus.publish(new AgentEvent.TurnStarted(request.sessionId(), now(request), request.turnId()));
                if (round == 0) {
                    publishPromptMessageEvents(request);
                } else if (!pendingMessages.isEmpty()) {
                    publishAndAppendQueuedMessages(request, pendingMessages, newMessages, modelMessages);
                    pendingMessages.clear();
                }
                RoundResult roundResult = runModelRoundWithRetries(request, modelMessages);
                usage = usage.plus(roundResult.usage());
                newMessages.add(roundResult.message());
                assistantMessages.add(roundResult.message());
                modelMessages.addAll(messageConverter.convertToLlm(List.of(roundResult.message())));

                List<ToolCall> toolCalls = toolCalls(roundResult.message());
                List<AgentMessage> roundToolResults = new ArrayList<>();
                if (roundResult.stopReason() != AiStopReason.TOOL_USE || toolCalls.isEmpty()) {
                    eventBus.publish(new AgentEvent.TurnEnded(
                            request.sessionId(),
                            now(request),
                            request.turnId(),
                            roundResult.message(),
                            roundToolResults,
                            roundResult.usage()));
                    pendingMessages.addAll(drainQueue(request, QueueKind.STEER, steeringQueue, request.steeringMode()));
                    if (!pendingMessages.isEmpty()) {
                        continue;
                    }
                    pendingMessages.addAll(drainQueue(request, QueueKind.FOLLOW_UP, followUpQueue, request.followUpMode()));
                    if (!pendingMessages.isEmpty()) {
                        continue;
                    }
                    eventBus.publish(new AgentEvent.AgentEnded(
                            request.sessionId(),
                            now(request),
                            request.turnId(),
                            newMessages,
                            usage));
                    return new AgentLoopResult(
                            List.copyOf(newMessages),
                            List.copyOf(assistantMessages),
                            List.copyOf(toolResults),
                            usage);
                }
                if (round == request.maxToolRounds()) {
                    throw new IllegalStateException("maximum tool rounds exceeded: " + request.maxToolRounds());
                }

                List<ToolResult> roundResults = executeToolCalls(request, toolCalls);
                for (ToolResult toolResult : roundResults) {
                    AgentMessage toolResultMessage = ToolResultAgentMessageView.toEnvelope(
                            toolResult,
                            roundResult.message().id(),
                            now(request));
                    toolResults.add(toolResult);
                    roundToolResults.add(toolResultMessage);
                    eventBus.publish(new AgentEvent.ToolExecutionEnded(request.sessionId(), now(request), toolResult));
                    eventBus.publish(new AgentEvent.MessageStarted(request.sessionId(), now(request), toolResultMessage));
                    eventBus.publish(new AgentEvent.MessageEnded(request.sessionId(), now(request), toolResultMessage));
                    newMessages.add(toolResultMessage);
                    modelMessages.addAll(messageConverter.convertToLlm(List.of(toolResultMessage)));
                }
                eventBus.publish(new AgentEvent.TurnEnded(
                        request.sessionId(),
                        now(request),
                        request.turnId(),
                        roundResult.message(),
                        roundToolResults,
                        roundResult.usage()));
                if (roundResults.stream().anyMatch(ToolResult::terminate)) {
                    eventBus.publish(new AgentEvent.AgentEnded(
                            request.sessionId(),
                            now(request),
                            request.turnId(),
                            newMessages,
                            usage));
                    return new AgentLoopResult(
                            List.copyOf(newMessages),
                            List.copyOf(assistantMessages),
                            List.copyOf(toolResults),
                            usage);
                }
                pendingMessages.addAll(drainQueue(request, QueueKind.STEER, steeringQueue, request.steeringMode()));
            }
            throw new IllegalStateException("agent loop ended without settling");
        } catch (AgentAbortException e) {
            eventBus.publish(new AgentEvent.AgentAborted(request.sessionId(), now(request), e.getMessage()));
            throw e;
        }
    }

    private List<ToolResult> executeToolCalls(AgentLoopRequest request, List<ToolCall> toolCalls) throws Exception {
        if (request.toolExecutionMode() == ToolExecutionMode.SEQUENTIAL || toolCalls.size() <= 1) {
            List<ToolResult> results = new ArrayList<>();
            for (ToolCall toolCall : toolCalls) {
                request.abortSignal().throwIfAborted();
                eventBus.publish(new AgentEvent.ToolExecutionStarted(request.sessionId(), now(request), toolCall));
                results.add(executeToolCall(request, toolCall));
            }
            return results;
        }
        for (ToolCall toolCall : toolCalls) {
            request.abortSignal().throwIfAborted();
            eventBus.publish(new AgentEvent.ToolExecutionStarted(request.sessionId(), now(request), toolCall));
        }
        ExecutorService executorService = Executors.newFixedThreadPool(toolCalls.size());
        try {
            List<Future<ToolResult>> futures = new ArrayList<>();
            for (ToolCall toolCall : toolCalls) {
                futures.add(executorService.submit(() -> executeToolCall(request, toolCall)));
            }
            List<ToolResult> results = new ArrayList<>();
            for (Future<ToolResult> future : futures) {
                request.abortSignal().throwIfAborted();
                results.add(awaitToolResult(future));
            }
            return results;
        } finally {
            executorService.shutdownNow();
        }
    }

    private ToolResult executeToolCall(AgentLoopRequest request, ToolCall toolCall) throws Exception {
        ToolContext context = toolContext(request, toolCall);
        Optional<ToolResult> blockedResult = Optional.empty();
        for (ToolExecutionHook hook : toolExecutionHooks) {
            request.abortSignal().throwIfAborted();
            blockedResult = hook.beforeToolExecution(toolCall, context);
            if (blockedResult.isPresent()) {
                break;
            }
        }
        ToolResult result = blockedResult.orElseGet(() -> toolExecutor.execute(toolCall, context));
        for (ToolExecutionHook hook : toolExecutionHooks) {
            request.abortSignal().throwIfAborted();
            hook.afterToolExecution(toolCall, context, result);
        }
        return result;
    }

    private static ToolResult awaitToolResult(Future<ToolResult> future) throws Exception {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }

    private void publishPromptMessageEvents(AgentLoopRequest request) {
        for (AgentMessage message : request.promptMessages()) {
            eventBus.publish(new AgentEvent.MessageStarted(request.sessionId(), now(request), message));
            eventBus.publish(new AgentEvent.MessageEnded(request.sessionId(), now(request), message));
        }
    }

    private void publishAndAppendQueuedMessages(
            AgentLoopRequest request,
            List<AgentMessage> messages,
            List<AgentMessage> newMessages,
            List<AiMessage> modelMessages
    ) {
        for (AgentMessage message : messages) {
            eventBus.publish(new AgentEvent.MessageStarted(request.sessionId(), now(request), message));
            eventBus.publish(new AgentEvent.MessageEnded(request.sessionId(), now(request), message));
            newMessages.add(message);
        }
        modelMessages.addAll(messageConverter.convertToLlm(messages));
    }

    private List<AgentMessage> drainQueue(
            AgentLoopRequest request,
            QueueKind queueKind,
            List<AgentMessage> queue,
            QueueMode mode
    ) {
        if (queue.isEmpty()) {
            return List.of();
        }
        List<AgentMessage> drained;
        if (mode == QueueMode.ALL) {
            drained = List.copyOf(queue);
            queue.clear();
        } else {
            drained = List.of(queue.removeFirst());
        }
        eventBus.publish(new AgentEvent.QueueUpdated(request.sessionId(), now(request), queueKind, queue.size()));
        return drained;
    }

    private RoundResult runModelRoundWithRetries(AgentLoopRequest request, List<AiMessage> modelMessages) throws Exception {
        int retryAttempt = 0;
        while (true) {
            try {
                RoundResult result = runModelRound(request, modelMessages);
                if (retryAttempt > 0) {
                    eventBus.publish(new AgentEvent.RetryCompleted(request.sessionId(), now(request), retryAttempt, true));
                }
                return result;
            } catch (AgentAbortException e) {
                throw e;
            } catch (Exception e) {
                if (retryAttempt >= request.maxModelRetries()) {
                    if (retryAttempt > 0) {
                        eventBus.publish(new AgentEvent.RetryCompleted(request.sessionId(), now(request), retryAttempt, false));
                    }
                    throw e;
                }
                retryAttempt++;
                eventBus.publish(new AgentEvent.RetryStarted(
                        request.sessionId(),
                        now(request),
                        retryAttempt,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
    }

    private RoundResult runModelRound(AgentLoopRequest request, List<AiMessage> modelMessages) throws Exception {
        List<AiStreamEvent> events = new ArrayList<>();
        modelClient.stream(new AiTurnRequest(modelMessages, toolSpecs()), event -> {
            request.abortSignal().throwIfAborted();
            events.add(event);
            publishModelEvent(request, event);
        });
        AiStreamEvent.MessageCompleted completed = events.stream()
                .filter(AiStreamEvent.MessageCompleted.class::isInstance)
                .map(AiStreamEvent.MessageCompleted.class::cast)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("model stream completed without a message"));
        AiAssistantMessage completedMessage = completed.message();
        AgentMessage message = new AgentMessage(
                completed.messageId(),
                request.parentMessageId(),
                now(request),
                AgentMessageRole.ASSISTANT,
                AiContentBlocks.toJsonArray(completedMessage.content()),
                JSON.objectNode());
        eventBus.publish(new AgentEvent.MessageEnded(request.sessionId(), now(request), message));
        return new RoundResult(message, completedMessage.stopReason(), toUsage(completedMessage.usage()));
    }

    private List<AiMessage> initialModelMessages(AgentLoopRequest request) {
        List<AiMessage> modelMessages = new ArrayList<>();
        if (request.systemPrompt() != null) {
            modelMessages.add(new AiSystemMessage(request.systemPrompt()));
        }
        modelMessages.addAll(messageConverter.convertToLlm(request.messages()));
        return modelMessages;
    }

    private void publishModelEvent(AgentLoopRequest request, AiStreamEvent event) {
        switch (event) {
            case AiStreamEvent.MessageStarted started -> eventBus.publish(new AgentEvent.MessageStarted(
                    request.sessionId(),
                    now(request),
                    new AgentMessage(
                            started.messageId(),
                            request.parentMessageId(),
                            now(request),
                            AgentMessageRole.ASSISTANT,
                            JSON.arrayNode(),
                            JSON.objectNode())));
            case AiStreamEvent.MessageErrored errored -> eventBus.publish(new AgentEvent.MessageUpdated(
                    request.sessionId(),
                    now(request),
                    errored.messageId(),
                    JSON.objectNode()
                            .put("type", "message_error")
                            .put("error", errored.error())));
            case AiStreamEvent.TextStarted started -> eventBus.publish(new AgentEvent.MessageUpdated(
                    request.sessionId(),
                    now(request),
                    started.messageId(),
                    JSON.objectNode()
                            .put("type", "text_start")
                            .put("contentIndex", started.contentIndex())));
            case AiStreamEvent.TextDelta delta -> eventBus.publish(new AgentEvent.MessageUpdated(
                    request.sessionId(),
                    now(request),
                    delta.messageId(),
                    JSON.objectNode()
                            .put("type", "text_delta")
                            .put("contentIndex", delta.contentIndex())
                            .put("delta", delta.delta())));
            case AiStreamEvent.TextEnded ended -> eventBus.publish(new AgentEvent.MessageUpdated(
                    request.sessionId(),
                    now(request),
                    ended.messageId(),
                    JSON.objectNode()
                            .put("type", "text_end")
                            .put("contentIndex", ended.contentIndex())));
            case AiStreamEvent.ThinkingStarted started -> eventBus.publish(new AgentEvent.MessageUpdated(
                    request.sessionId(),
                    now(request),
                    started.messageId(),
                    JSON.objectNode()
                            .put("type", "thinking_start")
                            .put("contentIndex", started.contentIndex())));
            case AiStreamEvent.ThinkingDelta delta -> eventBus.publish(new AgentEvent.MessageUpdated(
                    request.sessionId(),
                    now(request),
                    delta.messageId(),
                    JSON.objectNode()
                            .put("type", "thinking_delta")
                            .put("contentIndex", delta.contentIndex())
                            .put("delta", delta.delta())));
            case AiStreamEvent.ThinkingEnded ended -> eventBus.publish(new AgentEvent.MessageUpdated(
                    request.sessionId(),
                    now(request),
                    ended.messageId(),
                    JSON.objectNode()
                            .put("type", "thinking_end")
                            .put("contentIndex", ended.contentIndex())));
            case AiStreamEvent.ToolCallStarted started -> eventBus.publish(new AgentEvent.MessageUpdated(
                    request.sessionId(),
                    now(request),
                    started.messageId(),
                    JSON.objectNode()
                            .put("type", "toolcall_start")
                            .put("contentIndex", started.contentIndex())
                            .put("toolCallId", started.toolCallId())
                            .put("toolName", started.toolName())));
            case AiStreamEvent.ToolCallDelta delta -> eventBus.publish(new AgentEvent.MessageUpdated(
                    request.sessionId(),
                    now(request),
                    delta.messageId(),
                    JSON.objectNode()
                            .put("type", "toolcall_delta")
                            .put("contentIndex", delta.contentIndex())
                            .set("delta", delta.delta())));
            case AiStreamEvent.ToolCallEnded ended -> eventBus.publish(new AgentEvent.MessageUpdated(
                    request.sessionId(),
                    now(request),
                    ended.messageId(),
                    JSON.objectNode()
                            .put("type", "toolcall_end")
                            .put("contentIndex", ended.contentIndex())
                            .put("toolCallId", ended.toolCallId())));
            case AiStreamEvent.MessageCompleted ignored -> {
            }
        }
    }

    private List<AiToolSpec> toolSpecs() {
        return toolRegistry.specs().stream()
                .map(spec -> new AiToolSpec(spec.name(), spec.description(), spec.inputSchema()))
                .toList();
    }

    private ToolContext toolContext(AgentLoopRequest request, ToolCall toolCall) {
        return new ToolContext(
                request.sessionId(),
                request.cwd(),
                request.clock(),
                request.abortSignal(),
                request.toolAttributes(),
                update -> eventBus.publish(new AgentEvent.ToolExecutionUpdated(
                        request.sessionId(),
                        now(request),
                        toolCall.id(),
                        update)));
    }

    private static List<ToolCall> toolCalls(AgentMessage message) {
        return message.view() instanceof AssistantAgentMessageView assistant
                ? assistant.toolCalls()
                : List.of();
    }

    private static Usage toUsage(AiUsage usage) {
        return new Usage(usage.inputTokens(), usage.outputTokens(), usage.cachedInputTokens(), usage.reasoningTokens());
    }

    private static Instant now(AgentLoopRequest request) {
        return request.clock().instant();
    }

    private record RoundResult(AgentMessage message, AiStopReason stopReason, Usage usage) {
    }
}
