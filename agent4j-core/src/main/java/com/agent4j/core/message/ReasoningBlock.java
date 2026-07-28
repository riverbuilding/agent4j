package com.agent4j.core.message;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.Optional;

public record ReasoningBlock(String text, JsonNode raw) implements ContentBlock {
    public ReasoningBlock {
        Objects.requireNonNull(text, "text");
    }

    @Override
    public ContentBlockType type() {
        return ContentBlockType.REASONING;
    }

    @Override
    public Optional<String> textValue() {
        return Optional.of(text);
    }
}
