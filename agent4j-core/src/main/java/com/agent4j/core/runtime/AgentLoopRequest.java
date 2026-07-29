package com.agent4j.core.runtime;

import com.agent4j.core.message.AgentMessage;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AgentLoopRequest(
        String sessionId,
        String turnId,
        String parentMessageId,
        List<AgentMessage> messages,
        Path cwd,
        Clock clock,
        AbortSignal abortSignal,
        Map<String, Object> toolAttributes,
        int maxToolRounds
) {
    public AgentLoopRequest {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(turnId, "turnId");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(abortSignal, "abortSignal");
        if (maxToolRounds < 0) {
            throw new IllegalArgumentException("maxToolRounds must be non-negative");
        }
        messages = List.copyOf(messages);
        toolAttributes = toolAttributes == null ? Map.of() : Map.copyOf(toolAttributes);
    }
}
