package com.agent4j.core.message;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.Optional;

public record TextBlock(String text, JsonNode raw) implements ContentBlock {
    public TextBlock {
        Objects.requireNonNull(text, "text");
    }

    @Override
    public ContentBlockType type() {
        return ContentBlockType.TEXT;
    }

    @Override
    public Optional<String> textValue() {
        return Optional.of(text);
    }
}
