package com.agent4j.core.runtime;

import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiModelClient;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiContentBlock;
import com.agent4j.ai.AiContentBlocks;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiToolSpec;
import com.agent4j.ai.AiToolResultMessage;
import com.agent4j.ai.AiTurnRequest;
import com.agent4j.ai.AiUsage;
import com.agent4j.ai.AiUserMessage;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

    public AgentLoop(AiModelClient modelClient, ToolRegistry toolRegistry, AgentEventBus eventBus) {
        this.modelClient = Objects.requireNonNull(modelClient, "modelClient");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolExecutor = new ToolExecutor(toolRegistry);
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    public AgentLoopResult runTurn(AgentLoopRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        request.abortSignal().throwIfAborted();
        eventBus.publish(new AgentEvent.AgentStarted(request.sessionId(), now(request), request.turnId()));

        List<AiMessage> modelMessages = new ArrayList<>(toAiMessages(request.messages()));
        List<AgentMessage> assistantMessages = new ArrayList<>();
        List<ToolResult> toolResults = new ArrayList<>();
        Usage usage = Usage.zero();

        try {
            for (int round = 0; round <= request.maxToolRounds(); round++) {
                request.abortSignal().throwIfAborted();
                RoundResult roundResult = runModelRound(request, modelMessages);
                usage = usage.plus(roundResult.usage());
                assistantMessages.add(roundResult.message());
                modelMessages.add(toAiMessage(roundResult.message()));

                List<ToolCall> toolCalls = toolCalls(roundResult.message());
                if (roundResult.stopReason() != AiStopReason.TOOL_USE || toolCalls.isEmpty()) {
                    eventBus.publish(new AgentEvent.AgentSettled(request.sessionId(), now(request), request.turnId(), usage));
                    return new AgentLoopResult(List.copyOf(assistantMessages), List.copyOf(toolResults), usage);
                }
                if (round == request.maxToolRounds()) {
                    throw new IllegalStateException("maximum tool rounds exceeded: " + request.maxToolRounds());
                }

                for (ToolCall toolCall : toolCalls) {
                    request.abortSignal().throwIfAborted();
                    eventBus.publish(new AgentEvent.ToolExecutionStarted(request.sessionId(), now(request), toolCall));
                    ToolResult toolResult = toolExecutor.execute(toolCall, toolContext(request));
                    toolResults.add(toolResult);
                    eventBus.publish(new AgentEvent.ToolExecutionCompleted(request.sessionId(), now(request), toolResult));
                    modelMessages.add(toAiMessage(toolResult));
                }
            }
            throw new IllegalStateException("agent loop ended without settling");
        } catch (AgentAbortException e) {
            eventBus.publish(new AgentEvent.AgentAborted(request.sessionId(), now(request), e.getMessage()));
            throw e;
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
        eventBus.publish(new AgentEvent.MessageCompleted(request.sessionId(), now(request), message));
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
            case AiStreamEvent.TextDelta delta -> eventBus.publish(new AgentEvent.MessageDelta(
                    request.sessionId(),
                    now(request),
                    delta.messageId(),
                    JSON.objectNode()
                            .put("type", "text_delta")
                            .put("contentIndex", delta.contentIndex())
                            .put("delta", delta.delta())));
            case AiStreamEvent.ThinkingDelta delta -> eventBus.publish(new AgentEvent.MessageDelta(
                    request.sessionId(),
                    now(request),
                    delta.messageId(),
                    JSON.objectNode()
                            .put("type", "thinking_delta")
                            .put("contentIndex", delta.contentIndex())
                            .put("delta", delta.delta())));
            case AiStreamEvent.ToolCallDelta delta -> eventBus.publish(new AgentEvent.MessageDelta(
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

    private static List<AiMessage> toAiMessages(List<AgentMessage> messages) {
        return messages.stream().map(AgentLoop::toAiMessage).toList();
    }

    private static AiMessage toAiMessage(AgentMessage message) {
        List<AiContentBlock> content = AiContentBlocks.parse(message.content());
        return switch (message.role()) {
            case ASSISTANT -> new AiAssistantMessage(content, AiStopReason.STOP, AiUsage.zero());
            case TOOL_RESULT -> new AiToolResultMessage("", "", content, false);
            default -> new AiUserMessage(content);
        };
    }

    private static AiMessage toAiMessage(ToolResult result) {
        String text = result.content() == null || result.content().isNull()
                ? ""
                : result.content().isTextual() ? result.content().asText() : result.content().toString();
        return new AiToolResultMessage(
                result.toolCallId(),
                result.toolName(),
                List.of(new AiTextContent(text)),
                result.error());
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
