package com.agent4j.core.tool;

import com.agent4j.core.runtime.AbortSignal;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public record ToolContext(
        String sessionId,
        Path cwd,
        Clock clock,
        AbortSignal abortSignal,
        Map<String, Object> attributes,
        Consumer<JsonNode> updateSink
) {
    public ToolContext(
            String sessionId,
            Path cwd,
            Clock clock,
            AbortSignal abortSignal,
            Map<String, Object> attributes
    ) {
        this(sessionId, cwd, clock, abortSignal, attributes, ignored -> {
        });
    }

    public ToolContext {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(abortSignal, "abortSignal");
        attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
        updateSink = updateSink == null ? ignored -> {
        } : updateSink;
    }

    public Optional<Object> attribute(String name) {
        return Optional.ofNullable(attributes.get(name));
    }

    /** Adds a value that subsequent hooks and the executing tool can read for this tool call. */
    public void putAttribute(String name, Object value) {
        attributes.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
    }

    public void publishUpdate(JsonNode update) {
        Objects.requireNonNull(update, "update");
        abortSignal.throwIfAborted();
        updateSink.accept(update);
    }
}
