package com.agent4j.core.message;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ToolResultAgentMessageView(AgentMessage envelope) implements AgentMessageView {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    public ToolResultAgentMessageView {
        Objects.requireNonNull(envelope, "envelope");
    }

    public String toolCallId() {
        return textMetadata("toolCallId").orElse("");
    }

    public String toolName() {
        return textMetadata("toolName").orElse("");
    }

    public boolean error() {
        return envelope.metadata() != null && envelope.metadata().path("error").asBoolean(false);
    }

    public boolean blocked() {
        return envelope.metadata() != null && envelope.metadata().path("blocked").asBoolean(false);
    }

    public boolean terminate() {
        return envelope.metadata() != null && envelope.metadata().path("terminate").asBoolean(false);
    }

    public String text() {
        return envelope.textContent();
    }

    private Optional<String> textMetadata(String fieldName) {
        if (envelope.metadata() == null || !envelope.metadata().has(fieldName)) {
            return Optional.empty();
        }
        return Optional.of(envelope.metadata().get(fieldName).asText(""));
    }

    public static AgentMessage toEnvelope(ToolResult result, String parentId, Instant timestamp) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(timestamp, "timestamp");
        var metadata = JSON.objectNode()
                .put("toolCallId", result.toolCallId())
                .put("toolName", result.toolName())
                .put("error", result.error());
        if (result.metadata() != null && result.metadata().isObject()) {
            result.metadata().fields().forEachRemaining(field -> {
                if (!metadata.has(field.getKey())) {
                    metadata.set(field.getKey(), field.getValue());
                }
            });
        }
        return new AgentMessage(
                "tool-result-" + result.toolCallId(),
                parentId,
                timestamp,
                AgentMessageRole.TOOL_RESULT,
                result.content(),
                metadata);
    }
}
