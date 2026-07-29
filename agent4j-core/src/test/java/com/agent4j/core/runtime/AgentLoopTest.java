package com.agent4j.core.runtime;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiToolCallContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.ToolRegistry;
import com.agent4j.core.tool.ToolSpec;
import com.agent4j.testkit.ai.FakeModelClient;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void runsTextOnlyTurnAgainstFakeModel() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueue(List.of(
                new AiStreamEvent.MessageStarted("assistant-1"),
                new AiStreamEvent.TextDelta("assistant-1", 0, "hello"),
                new AiStreamEvent.MessageCompleted(
                        "assistant-1",
                        new AiAssistantMessage(
                                List.of(new AiTextContent("hello")),
                                AiStopReason.STOP,
                                new AiUsage(3, 2, 1, 0)))));
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        AgentLoopResult result = new AgentLoop(model, InMemoryToolRegistry.builder().build(), bus)
                .runTurn(request(List.of(userMessage("user-1", "say hi")), 2));

        assertThat(result.assistantMessages()).hasSize(1);
        assertThat(result.assistantMessages().getFirst().textContent()).isEqualTo("hello");
        assertThat(result.toolResults()).isEmpty();
        assertThat(result.usage()).isEqualTo(new Usage(3, 2, 1, 0));
        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsExactly("AgentStarted", "MessageStarted", "MessageDelta", "MessageCompleted", "AgentSettled");
        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().getFirst().messages()).hasSize(1);
    }

    @Test
    void executesToolCallsAndContinuesUntilTerminalMessage() throws Exception {
        ToolCall toolCall = new ToolCall("tool-1", "echo", JSON.objectNode().put("text", "hello"));
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                                        AiStopReason.TOOL_USE,
                                        new AiUsage(5, 1, 0, 0)))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("done")),
                                        AiStopReason.STOP,
                                        new AiUsage(4, 2, 0, 0)))));
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("echo", "Echo text", JSON.objectNode().put("type", "object")), (call, context) ->
                        new ToolResult(call.id(), call.name(), false, call.arguments().get("text"), JSON.objectNode()))
                .build();
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        AgentLoopResult result = new AgentLoop(model, registry, bus)
                .runTurn(request(List.of(userMessage("user-1", "echo hello")), 2));

        assertThat(result.assistantMessages()).extracting(AgentMessage::id)
                .containsExactly("assistant-1", "assistant-2");
        assertThat(result.assistantMessages().getLast().textContent()).isEqualTo("done");
        assertThat(result.toolResults()).hasSize(1);
        assertThat(result.toolResults().getFirst().content().asText()).isEqualTo("hello");
        assertThat(result.usage()).isEqualTo(new Usage(9, 3, 0, 0));
        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsExactly(
                        "AgentStarted",
                        "MessageStarted",
                        "MessageCompleted",
                        "ToolExecutionStarted",
                        "ToolExecutionCompleted",
                        "MessageStarted",
                        "MessageCompleted",
                        "AgentSettled");
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().getLast().role()).isEqualTo("toolResult");
        assertThat(((com.agent4j.ai.AiToolResultMessage) model.requests().get(1).messages().getLast())
                .content().getFirst()).isEqualTo(new AiTextContent("hello"));
    }

    private AgentLoopRequest request(List<AgentMessage> messages, int maxToolRounds) {
        return new AgentLoopRequest(
                "session-1",
                "turn-1",
                messages.getLast().id(),
                messages,
                Path.of("/repo"),
                clock,
                new AbortController().signal(),
                Map.of(),
                maxToolRounds);
    }

    private AgentMessage userMessage(String id, String text) {
        return new AgentMessage(
                id,
                null,
                Instant.now(clock),
                AgentMessageRole.USER,
                ContentBlocks.toJsonArray(List.of(new TextBlock(text, null))),
                JSON.objectNode());
    }
}
