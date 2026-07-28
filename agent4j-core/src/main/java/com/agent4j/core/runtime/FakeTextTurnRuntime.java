package com.agent4j.core.runtime;

import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class FakeTextTurnRuntime {
    private final AgentEventBus eventBus;
    private final Clock clock;
    private final ObjectMapper mapper;

    public FakeTextTurnRuntime(AgentEventBus eventBus, Clock clock) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.mapper = new ObjectMapper();
    }

    public AgentMessage emitAssistantTextTurn(String sessionId, String turnId, String messageId, String text, AbortSignal signal) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(turnId, "turnId");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(signal, "signal");

        signal.throwIfAborted();
        eventBus.publish(new AgentEvent.AgentStarted(sessionId, now(), turnId));

        AgentMessage started = new AgentMessage(
                messageId,
                null,
                now(),
                AgentMessageRole.ASSISTANT,
                mapper.createArrayNode(),
                mapper.createObjectNode().put("turnId", turnId));
        eventBus.publish(new AgentEvent.MessageStarted(sessionId, now(), started));

        ObjectNode delta = mapper.createObjectNode();
        delta.put("text", text);
        eventBus.publish(new AgentEvent.MessageDelta(sessionId, now(), messageId, delta));

        signal.throwIfAborted();
        ArrayNode content = mapper.createArrayNode();
        ObjectNode textBlock = mapper.createObjectNode();
        textBlock.put("type", "text");
        textBlock.put("text", text);
        content.add(textBlock);
        AgentMessage completed = new AgentMessage(
                messageId,
                null,
                now(),
                AgentMessageRole.ASSISTANT,
                content,
                mapper.createObjectNode().put("turnId", turnId));
        eventBus.publish(new AgentEvent.MessageCompleted(sessionId, now(), completed));
        eventBus.publish(new AgentEvent.AgentSettled(sessionId, now(), turnId, Usage.zero()));
        return completed;
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
