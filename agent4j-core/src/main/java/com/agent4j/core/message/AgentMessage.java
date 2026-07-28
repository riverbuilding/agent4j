package com.agent4j.core.message;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record AgentMessage(
        String id,
        String parentId,
        Instant timestamp,
        AgentMessageRole role,
        JsonNode content,
        JsonNode metadata
) {
    public AgentMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(role, "role");
    }

    public Optional<String> optionalParentId() {
        return Optional.ofNullable(parentId);
    }

    public Optional<JsonNode> optionalContent() {
        return content == null || content.isNull() ? Optional.empty() : Optional.of(content);
    }

    public Optional<JsonNode> optionalMetadata() {
        return metadata == null || metadata.isNull() ? Optional.empty() : Optional.of(metadata);
    }
}
