package com.agent4j.core.event;

import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.runtime.FakeAssistantTextTurnEmitter;
import com.agent4j.core.runtime.QueueKind;
import com.agent4j.core.runtime.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEventBusTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void emitsCompleteTextTurnEventSequence() {
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);
        FakeAssistantTextTurnEmitter emitter = new FakeAssistantTextTurnEmitter(bus, clock);

        emitter.emitAssistantTextTurn(
                "session-1",
                "turn-1",
                "message-1",
                "hello",
                new com.agent4j.core.runtime.AbortController().signal());

        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsExactly(
                        "AgentStarted",
                        "TurnStarted",
                        "MessageStarted",
                        "MessageUpdated",
                        "MessageEnded",
                        "TurnEnded",
                        "AgentEnded");
        assertThat(events).extracting(AgentEvent::wireName)
                .containsExactly(
                        AgentEvent.AgentStarted.TYPE,
                        AgentEvent.TurnStarted.TYPE,
                        AgentEvent.MessageStarted.TYPE,
                        AgentEvent.MessageUpdated.TYPE,
                        AgentEvent.MessageEnded.TYPE,
                        AgentEvent.TurnEnded.TYPE,
                        AgentEvent.AgentEnded.TYPE);
        assertThat(((AgentEvent.MessageEnded) events.get(4)).message().content().get(0).get("text").asText())
                .isEqualTo("hello");
        assertThat(((AgentEvent.MessageEnded) events.get(4)).message().textContent()).isEqualTo("hello");
    }

    @Test
    void unsubscribeStopsReceivingEvents() {
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        EventSubscription subscription = bus.subscribe(events::add);

        bus.publish(new AgentEvent.AgentStarted("session-1", Instant.now(clock), "turn-1"));
        subscription.close();
        bus.publish(new AgentEvent.AgentStarted("session-1", Instant.now(clock), "turn-2"));

        assertThat(events).hasSize(1);
        assertThat(bus.subscriberCount()).isZero();
    }

    @Test
    void serializesEventsWithStableTypeDiscriminator() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        AgentEvent event = new AgentEvent.AgentEnded(
                "session-1",
                Instant.parse("2026-07-28T10:00:00Z"),
                "turn-1",
                List.of(),
                com.agent4j.core.runtime.Usage.zero());

        String json = mapper.writeValueAsString(event);
        AgentEvent readBack = mapper.readValue(json, AgentEvent.class);

        assertThat(json).contains("\"type\":\"" + event.wireName() + "\"");
        assertThat(readBack).isInstanceOf(AgentEvent.AgentEnded.class);
        assertThat(((AgentEvent.AgentEnded) readBack).turnId()).isEqualTo("turn-1");
    }

    @Test
    void serializesPhase4OperationalEventsWithStablePayloads() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        AgentMessage assistant = new AgentMessage(
                "assistant-1",
                "user-1",
                Instant.now(clock),
                AgentMessageRole.ASSISTANT,
                ContentBlocks.toJsonArray(List.of(new TextBlock("done", null))),
                JSON.objectNode());
        AgentMessage toolResultMessage = new AgentMessage(
                "tool-result-tool-1",
                "assistant-1",
                Instant.now(clock),
                AgentMessageRole.TOOL_RESULT,
                JSON.textNode("content"),
                JSON.objectNode()
                        .put("toolCallId", "tool-1")
                        .put("toolName", "read")
                        .put("error", false));
        List<AgentEvent> events = List.of(
                new AgentEvent.QueueUpdated("session-1", Instant.now(clock), QueueKind.STEER, 2),
                new AgentEvent.RetryStarted("session-1", Instant.now(clock), 1, "temporary"),
                new AgentEvent.RetryCompleted("session-1", Instant.now(clock), 1, false),
                new AgentEvent.ToolExecutionStarted(
                        "session-1",
                        Instant.now(clock),
                        new ToolCall("tool-1", "read", JSON.objectNode().put("path", "README.md"))),
                new AgentEvent.ToolExecutionUpdated(
                        "session-1",
                        Instant.now(clock),
                        "tool-1",
                        JSON.objectNode().put("status", "running")),
                new AgentEvent.ToolExecutionEnded(
                        "session-1",
                        Instant.now(clock),
                        new ToolResult("tool-1", "read", false, JSON.textNode("content"), JSON.objectNode())),
                new AgentEvent.TurnEnded(
                        "session-1",
                        Instant.now(clock),
                        "turn-1",
                        assistant,
                        List.of(toolResultMessage),
                        new Usage(1, 2, 0, 0)),
                new AgentEvent.AgentAborted("session-1", Instant.now(clock), "stop"));

        List<JsonNode> serialized = events.stream()
                .map(event -> {
                    try {
                        return mapper.readTree(mapper.writeValueAsString(event));
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                })
                .toList();

        assertThat(serialized).extracting(node -> node.path("type").asText())
                .containsExactly(
                        "queue_updated",
                        "retry_started",
                        "retry_completed",
                        "tool_execution_start",
                        "tool_execution_update",
                        "tool_execution_end",
                        "turn_end",
                        "agent_aborted");
        assertThat(serialized.get(0).path("queueKind").asText()).isEqualTo("steer");
        assertThat(serialized.get(1).path("reason").asText()).isEqualTo("temporary");
        assertThat(serialized.get(2).path("success").asBoolean()).isFalse();
        assertThat(serialized.get(4).path("toolCallId").asText()).isEqualTo("tool-1");
        assertThat(serialized.get(6).path("toolResults").get(0).path("id").asText()).isEqualTo("tool-result-tool-1");
        assertThat(serialized.get(7).path("reason").asText()).isEqualTo("stop");
    }
}
