package com.agent4j.cli;

import com.agent4j.core.event.AgentEvent;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonEventSerializerTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void serializesAbortAsPublicPiEventWithoutInternalEnvelopeFields() {
        String event = new JsonEventSerializer().serialize(new JsonEventSerializer().event(
                new AgentEvent.AgentAborted("session-1", Instant.parse("2026-08-11T00:00:00Z"), "cancelled")));

        assertThat(event).isEqualTo(fixtureLines().getFirst());
        assertThat(event).doesNotContain("sessionId", "timestamp", "AgentAborted");
    }

    @Test
    void serializesToolEventsWithPiFieldNames() {
        var call = new com.agent4j.core.message.ToolCall("call-1", "read", JSON.objectNode().put("path", "README.md"));
        String event = new JsonEventSerializer().serialize(new JsonEventSerializer().event(
                new AgentEvent.ToolExecutionStarted("session-1", Instant.EPOCH, call)));

        assertThat(event).isEqualTo(fixtureLines().get(1));
    }

    private static List<String> fixtureLines() {
        try (var stream = JsonEventSerializerTest.class.getResourceAsStream("/json-events/serializer-events.jsonl")) {
            if (stream == null) {
                throw new IllegalStateException("JSON event fixture is missing");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }
}
