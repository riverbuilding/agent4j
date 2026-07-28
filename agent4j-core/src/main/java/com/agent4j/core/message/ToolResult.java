package com.agent4j.core.message;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.Optional;

public record ToolResult(
        String toolCallId,
        String toolName,
        boolean error,
        JsonNode content,
        JsonNode metadata
) {
    public ToolResult {
        Objects.requireNonNull(toolCallId, "toolCallId");
        Objects.requireNonNull(toolName, "toolName");
    }

    public Optional<JsonNode> optionalMetadata() {
        return metadata == null || metadata.isNull() ? Optional.empty() : Optional.of(metadata);
    }
}
