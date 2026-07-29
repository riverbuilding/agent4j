package com.agent4j.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record AiToolSpec(String name, String description, JsonNode inputSchema) {
    public AiToolSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
    }
}
