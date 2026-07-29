package com.agent4j.core.event;

import com.agent4j.core.runtime.FakeTextTurnRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEventBusTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void emitsCompleteTextTurnEventSequence() {
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);
        FakeTextTurnRuntime runtime = new FakeTextTurnRuntime(bus, clock);

        runtime.emitAssistantTextTurn(
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

        assertThat(json).contains("\"type\":\"agent_end\"");
        assertThat(readBack).isInstanceOf(AgentEvent.AgentEnded.class);
        assertThat(((AgentEvent.AgentEnded) readBack).turnId()).isEqualTo("turn-1");
    }
}
