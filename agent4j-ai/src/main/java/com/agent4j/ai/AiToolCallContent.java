package com.agent4j.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record AiToolCallContent(String id, String name, JsonNode arguments, String thoughtSignature) implements AiContentBlock {
    public AiToolCallContent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }

    public AiToolCallContent(String id, String name, JsonNode arguments) {
        this(id, name, arguments, null);
    }

    @Override
    public String type() {
        return "toolCall";
    }
}
