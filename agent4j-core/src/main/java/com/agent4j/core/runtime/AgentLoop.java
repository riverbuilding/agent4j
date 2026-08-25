package com.agent4j.core.runtime;

import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiAbortSignal;
import com.agent4j.ai.AiGenerationOptions;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderContext;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiProviderSelection;
import com.agent4j.ai.AiRetryClassifier;
import com.agent4j.ai.AiResolvedAuth;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiContentBlocks;
import com.agent4j.ai.AiToolSpec;
import com.agent4j.ai.AiTurnRequest;
import com.agent4j.ai.AiUsage;
import com.agent4j.core.compaction.CompactionService;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.AssistantAgentMessageView;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.message.ToolResultAgentMessageView;
import com.agent4j.core.tool.ToolExecutionHook;
import com.agent4j.core.tool.ToolRegistry;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class AgentLoop {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ModelRoundStreamer modelStreamer;
    private final ToolRegistry toolRegistry;
    private final AgentLoopToolRoundExecutor toolRoundExecutor;
    private final AgentEventBus eventBus;
    private final AgentMessageConverter messageConverter;
    private final AgentLoopCompactor compactor;

    public AgentLoop(
            AiProvider provider,
            AiModel model,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus
    ) {
        this(provider, model, toolRegistry, eventBus, DefaultAgentMessageConverter.INSTANCE);
    }

    public AgentLoop(
            AiProviderSelection selection,
            AiResolvedAuth auth,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus
    ) {
        this(selection, auth, toolRegistry, eventBus, DefaultAgentMessageConverter.INSTANCE);
    }

    public AgentLoop(
            AiProviderSelection selection,
            AiResolvedAuth auth,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus,
            AgentMessageConverter messageConverter
    ) {
        this(selection, auth, toolRegistry, eventBus, messageConverter, List.of());
    }

    public AgentLoop(
            AiProviderSelection selection,
            AiResolvedAuth auth,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus,
            AgentMessageConverter messageConverter,
            List<ToolExecutionHook> toolExecutionHooks
    ) {
        this(
                selection.provider(),
                selection.model(),
                auth,
                toolRegistry,
                eventBus,
                messageConverter,
                toolExecutionHooks);
    }

    public AgentLoop(
            AiProvider provider,
            AiModel model,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus,
            AgentMessageConverter messageConverter
    ) {
        this(provider, model, toolRegistry, eventBus, messageConverter, List.of());
    }

    public AgentLoop(
            AiProvider provider,
            AiModel model,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus,
            AgentMessageConverter messageConverter,
            List<ToolExecutionHook> toolExecutionHooks
    ) {
        this(provider, model, AiResolvedAuth.none(), toolRegistry, eventBus, messageConverter, toolExecutionHooks);
    }

    public AgentLoop(
            AiProvider provider,
            AiModel model,
            AiResolvedAuth auth,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus
    ) {
        this(provider, model, auth, toolRegistry, eventBus, DefaultAgentMessageConverter.INSTANCE);
    }

    public AgentLoop(
            AiProvider provider,
            AiModel model,
            AiResolvedAuth auth,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus,
            AgentMessageConverter messageConverter
    ) {
        this(provider, model, auth, toolRegistry, eventBus, messageConverter, List.of());
    }

    public AgentLoop(
            AiProvider provider,
            AiModel model,
            AiResolvedAuth auth,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus,
            AgentMessageConverter messageConverter,
            List<ToolExecutionHook> toolExecutionHooks
    ) {
        this(
                adaptProvider(provider, model, auth),
                toolRegistry,
                eventBus,
                messageConverter,
                toolExecutionHooks,
                new CompactionService(),
                provider,
                model,
                auth);
    }

    private AgentLoop(
            ModelRoundStreamer modelStreamer,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus,
            AgentMessageConverter messageConverter,
            List<ToolExecutionHook> toolExecutionHooks,
            CompactionService compactionService,
            AiProvider compactionProvider,
            AiModel compactionModel,
            AiResolvedAuth compactionAuth
    ) {
        this.modelStreamer = Objects.requireNonNull(modelStreamer, "modelStreamer");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.messageConverter = Objects.requireNonNull(messageConverter, "messageConverter");
        this.toolRoundExecutor = new AgentLoopToolRoundExecutor(toolRegistry, eventBus, toolExecutionHooks);
        this.compactor = new AgentLoopCompactor(
                compactionService,
                compactionProvider,
                compactionModel,
                compactionAuth == null ? AiResolvedAuth.none() : compactionAuth,
                eventBus);
    }

    public AgentLoopResult runTurn(AgentLoopRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        request.abortSignal().throwIfAborted();
        eventBus.publish(new AgentEvent.AgentStarted(request.sessionId(), now(request), request.turnId()));

        AgentConversationContext conversation = new AgentConversationContext(request.messages(), request.promptMessages());
        List<ToolResult> toolResults = new ArrayList<>();
        LiveAgentQueues liveQueues = request.liveQueues();
        List<AgentMessage> pendingMessages = new ArrayList<>();
        Usage usage = Usage.zero();

        try {
            int toolRounds = 0;
            for (int round = 0; ; round++) {
                request.abortSignal().throwIfAborted();
                eventBus.publish(new AgentEvent.TurnStarted(request.sessionId(), now(request), request.turnId()));
                if (round == 0) {
                    publishPromptMessageEvents(request);
                } else if (!pendingMessages.isEmpty()) {
                    publishAndAppendQueuedMessages(request, pendingMessages, conversation);
                    pendingMessages.clear();
                }
                compactForThresholdIfNeeded(request, conversation);
                RoundResult roundResult;
                try {
                    roundResult = runModelRoundWithRetries(request, modelMessages(request, conversation));
                } catch (Exception e) {
                    compactForOverflowAndRetryIfPossible(request, conversation, e);
                    roundResult = runModelRoundWithRetries(request, modelMessages(request, conversation));
                }
                usage = usage.plus(roundResult.usage());
                conversation.appendGenerated(roundResult.message());

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
                    pendingMessages.addAll(drainQueue(request, QueueKind.STEER, liveQueues, request.steeringMode()));
                    if (!pendingMessages.isEmpty()) {
                        continue;
                    }
                    pendingMessages.addAll(drainQueue(request, QueueKind.FOLLOW_UP, liveQueues, request.followUpMode()));
                    if (!pendingMessages.isEmpty()) {
                        continue;
                    }
                    eventBus.publish(new AgentEvent.AgentEnded(
                            request.sessionId(),
                            now(request),
                            request.turnId(),
                            conversation.generatedMessages(),
                            usage));
                    return new AgentLoopResult(
                            conversation.generatedMessages(),
                            conversation.assistantMessages(),
                            List.copyOf(toolResults),
                            usage);
                }
                if (toolRounds == request.maxToolRounds()) {
                    throw new IllegalStateException("maximum tool rounds exceeded: " + request.maxToolRounds());
                }
                toolRounds++;

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
                    conversation.appendGenerated(toolResultMessage);
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
                            conversation.generatedMessages(),
                            usage));
                    return new AgentLoopResult(
                            conversation.generatedMessages(),
                            conversation.assistantMessages(),
                            List.copyOf(toolResults),
                            usage);
                }
                pendingMessages.addAll(drainQueue(request, QueueKind.STEER, liveQueues, request.steeringMode()));
            }
        } catch (AgentAbortException e) {
            eventBus.publish(new AgentEvent.AgentAborted(request.sessionId(), now(request), e.getMessage()));
            throw e;
        }
    }

    private List<ToolResult> executeToolCalls(AgentLoopRequest request, List<ToolCall> toolCalls) throws Exception {
        return toolRoundExecutor.execute(request, toolCalls);
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
            AgentConversationContext conversation
    ) {
        for (AgentMessage message : messages) {
            eventBus.publish(new AgentEvent.MessageStarted(request.sessionId(), now(request), message));
            eventBus.publish(new AgentEvent.MessageEnded(request.sessionId(), now(request), message));
            conversation.appendGenerated(message);
        }
    }

    private void compactForThresholdIfNeeded(
            AgentLoopRequest request,
            AgentConversationContext conversation
    ) throws Exception {
        compactor.compactForThreshold(request, conversation);
    }

    private void compactForOverflowAndRetryIfPossible(
            AgentLoopRequest request,
            AgentConversationContext conversation,
            Exception failure
    ) throws Exception {
        compactor.compactForOverflow(request, conversation, failure);
    }

    private List<AgentMessage> drainQueue(
            AgentLoopRequest request,
            QueueKind queueKind,
            LiveAgentQueues queues,
            QueueMode mode
    ) {
        List<AgentMessage> drained = queues.drain(queueKind, mode);
        if (!drained.isEmpty()) {
            eventBus.publish(new AgentEvent.QueueUpdated(request.sessionId(), now(request), queueKind, queues.size(queueKind)));
        }
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
                if (AgentLoopCompactor.isContextOverflow(e)) {
                    throw e;
                }
                if (!AiRetryClassifier.isRetryable(e)) {
                    throw e;
                }
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
        modelStreamer.stream(request, new AiTurnRequest(modelMessages, toolSpecs()), event -> {
            request.abortSignal().throwIfAborted();
            events.add(event);
            publishModelEvent(request, event);
        });
        events.stream()
                .filter(AiStreamEvent.MessageErrored.class::isInstance)
                .map(AiStreamEvent.MessageErrored.class::cast)
                .reduce((first, second) -> second)
                .ifPresent(error -> {
                    throw new IllegalStateException("model stream error: " + error.error());
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

    private List<AiMessage> modelMessages(AgentLoopRequest request, AgentConversationContext conversation) {
        return conversation.toModelMessages(request.systemPrompt(), messageConverter);
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

    @FunctionalInterface
    private interface ModelRoundStreamer {
        void stream(AgentLoopRequest request, AiTurnRequest turnRequest, Consumer<AiStreamEvent> sink) throws Exception;
    }

    private static ModelRoundStreamer adaptProvider(AiProvider provider, AiModel model, AiResolvedAuth auth) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(auth, "auth");
        return (request, turnRequest, sink) -> provider.stream(
                new AiProviderRequest(model, turnRequest, providerContext(request, auth), streamOptions(request)),
                sink);
    }

    private static AiProviderContext providerContext(AgentLoopRequest request, AiResolvedAuth auth) {
        return new AiProviderContext(
                Optional.of(request.sessionId()),
                Optional.of(request.turnId()),
                Optional.of(request.cwd()),
                auth,
                Map.of(),
                providerAttributes(request));
    }

    private static Map<String, Object> providerAttributes(AgentLoopRequest request) {
        if (request.parentMessageId() == null || request.parentMessageId().isBlank()) {
            return Map.of("maxToolRounds", request.maxToolRounds());
        }
        return Map.of(
                "parentMessageId", request.parentMessageId(),
                "maxToolRounds", request.maxToolRounds());
    }

    private static AiStreamOptions streamOptions(AgentLoopRequest request) {
        return new AiStreamOptions(
                new AiAbortSignal() {
                    @Override
                    public boolean aborted() {
                        return request.abortSignal().aborted();
                    }

                    @Override
                    public void throwIfAborted() {
                        request.abortSignal().throwIfAborted();
                    }
                },
                request.modelTimeout(),
                0,
                Map.of(),
                Map.of(),
                new AiGenerationOptions(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        request.options().toolChoice(),
                        true,
                        Map.of()));
    }
}
