package com.agent4j.core.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record ToolSpec(String name, String description, JsonNode inputSchema) {
    public ToolSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(inputSchema, "inputSchema");
        if (name.isBlank()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
    }
}
