package com.agent4j.core.message;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record ToolCallBlock(ToolCall toolCall, JsonNode raw) implements ContentBlock {
    public ToolCallBlock {
        Objects.requireNonNull(toolCall, "toolCall");
    }

    @Override
    public ContentBlockType type() {
        return ContentBlockType.TOOL_CALL;
    }
}
