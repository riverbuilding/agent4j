package com.agent4j.core.message;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record ToolCall(String id, String name, JsonNode arguments) {
    public ToolCall {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }
}
