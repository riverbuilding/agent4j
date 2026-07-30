package com.agent4j.core.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

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

    public boolean terminate() {
        return metadata != null && metadata.path("terminate").asBoolean(false);
    }

    public static ToolResult blocked(ToolCall call, String reason) {
        Objects.requireNonNull(call, "call");
        String message = reason == null || reason.isBlank() ? "tool execution blocked" : reason;
        var metadata = JsonNodeFactory.instance.objectNode()
                .put("message", message)
                .put("blocked", true);
        return new ToolResult(call.id(), call.name(), true, TextNode.valueOf(message), metadata);
    }

    public static ToolResult terminate(ToolCall call, JsonNode content, String reason) {
        Objects.requireNonNull(call, "call");
        var metadata = JsonNodeFactory.instance.objectNode()
                .put("terminate", true);
        if (reason != null && !reason.isBlank()) {
            metadata.put("terminateReason", reason);
        }
        return new ToolResult(call.id(), call.name(), false, content, metadata);
    }
}
