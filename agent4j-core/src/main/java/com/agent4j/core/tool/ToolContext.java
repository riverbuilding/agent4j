package com.agent4j.core.tool;

import com.agent4j.core.runtime.AbortSignal;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ToolContext(
        String sessionId,
        Path cwd,
        Clock clock,
        AbortSignal abortSignal,
        Map<String, Object> attributes
) {
    public ToolContext {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(abortSignal, "abortSignal");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public Optional<Object> attribute(String name) {
        return Optional.ofNullable(attributes.get(name));
    }
}
