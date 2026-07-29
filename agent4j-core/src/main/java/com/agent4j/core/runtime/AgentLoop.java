package com.agent4j.core.runtime;

import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiModelClient;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiContentBlocks;
import com.agent4j.ai.AiToolSpec;
import com.agent4j.ai.AiTurnRequest;
import com.agent4j.ai.AiUsage;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolCallBlock;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.tool.ToolContext;
import com.agent4j.core.tool.ToolExecutor;
import com.agent4j.core.tool.ToolRegistry;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AgentLoop {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final AiModelClient modelClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final AgentEventBus eventBus;
    private final AgentMessageConverter messageConverter;

    public AgentLoop(AiModelClient modelClient, ToolRegistry toolRegistry, AgentEventBus eventBus) {
        this(modelClient, toolRegistry, eventBus, DefaultAgentMessageConverter.INSTANCE);
    }

    public AgentLoop(
            AiModelClient modelClient,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus,
            AgentMessageConverter messageConverter
    ) {
        this.modelClient = Objects.requireNonNull(modelClient, "modelClient");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolExecutor = new ToolExecutor(toolRegistry);
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.messageConverter = Objects.requireNonNull(messageConverter, "messageConverter");
    }

    public AgentLoopResult runTurn(AgentLoopRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        request.abortSignal().throwIfAborted();
        eventBus.publish(new AgentEvent.AgentStarted(request.sessionId(), now(request), request.turnId()));

        List<AiMessage> modelMessages = new ArrayList<>(messageConverter.convertToLlm(request.messages()));
        List<AgentMessage> newMessages = new ArrayList<>();
        List<AgentMessage> assistantMessages = new ArrayList<>();
        List<ToolResult> toolResults = new ArrayList<>();
        Usage usage = Usage.zero();

        try {
            for (int round = 0; round <= request.maxToolRounds(); round++) {
                request.abortSignal().throwIfAborted();
                eventBus.publish(new AgentEvent.TurnStarted(request.sessionId(), now(request), request.turnId()));
                if (round == 0) {
                    publishPromptMessageEvents(request);
                }
                RoundResult roundResult = runModelRound(request, modelMessages);
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

                for (ToolCall toolCall : toolCalls) {
                    request.abortSignal().throwIfAborted();
                    eventBus.publish(new AgentEvent.ToolExecutionStarted(request.sessionId(), now(request), toolCall));
                    ToolResult toolResult = toolExecutor.execute(toolCall, toolContext(request));
                    AgentMessage toolResultMessage = toAgentMessage(toolResult, roundResult.message().id(), now(request));
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
            }
            throw new IllegalStateException("agent loop ended without settling");
        } catch (AgentAbortException e) {
            eventBus.publish(new AgentEvent.AgentAborted(request.sessionId(), now(request), e.getMessage()));
            throw e;
        }
    }

    private void publishPromptMessageEvents(AgentLoopRequest request) {
        request.messages().stream()
                .filter(message -> Objects.equals(message.id(), request.parentMessageId()))
                .filter(message -> message.role() == AgentMessageRole.USER)
                .findFirst()
                .ifPresent(message -> {
                    eventBus.publish(new AgentEvent.MessageStarted(request.sessionId(), now(request), message));
                    eventBus.publish(new AgentEvent.MessageEnded(request.sessionId(), now(request), message));
                });
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
            case AiStreamEvent.TextDelta delta -> eventBus.publish(new AgentEvent.MessageUpdated(
                    request.sessionId(),
                    now(request),
                    delta.messageId(),
                    JSON.objectNode()
                            .put("type", "text_delta")
                            .put("contentIndex", delta.contentIndex())
                            .put("delta", delta.delta())));
            case AiStreamEvent.ThinkingDelta delta -> eventBus.publish(new AgentEvent.MessageUpdated(
                    request.sessionId(),
                    now(request),
                    delta.messageId(),
                    JSON.objectNode()
                            .put("type", "thinking_delta")
                            .put("contentIndex", delta.contentIndex())
                            .put("delta", delta.delta())));
            case AiStreamEvent.ToolCallDelta delta -> eventBus.publish(new AgentEvent.MessageUpdated(
                    request.sessionId(),
                    now(request),
                    delta.messageId(),
                    JSON.objectNode()
                            .put("type", "toolcall_delta")
                            .put("contentIndex", delta.contentIndex())
                            .set("delta", delta.delta())));
            case AiStreamEvent.MessageCompleted ignored -> {
            }
        }
    }

    private List<AiToolSpec> toolSpecs() {
        return toolRegistry.specs().stream()
                .map(spec -> new AiToolSpec(spec.name(), spec.description(), spec.inputSchema()))
                .toList();
    }

    private ToolContext toolContext(AgentLoopRequest request) {
        return new ToolContext(request.sessionId(), request.cwd(), request.clock(), request.abortSignal(), request.toolAttributes());
    }

    private static AgentMessage toAgentMessage(ToolResult result, String parentId, Instant timestamp) {
        return new AgentMessage(
                "tool-result-" + result.toolCallId(),
                parentId,
                timestamp,
                AgentMessageRole.TOOL_RESULT,
                result.content(),
                JSON.objectNode()
                        .put("toolCallId", result.toolCallId())
                        .put("toolName", result.toolName())
                        .put("error", result.error()));
    }

    private static List<ToolCall> toolCalls(AgentMessage message) {
        return message.contentBlocks().stream()
                .filter(ToolCallBlock.class::isInstance)
                .map(ToolCallBlock.class::cast)
                .map(ToolCallBlock::toolCall)
                .toList();
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
