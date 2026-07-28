package com.agent4j.core.message;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record UnknownContentBlock(String rawType, JsonNode raw) implements ContentBlock {
    public UnknownContentBlock {
        Objects.requireNonNull(raw, "raw");
    }

    @Override
    public ContentBlockType type() {
        return ContentBlockType.UNKNOWN;
    }
}
