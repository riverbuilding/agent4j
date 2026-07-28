package com.agent4j.core.message;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
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

    public List<ContentBlock> contentBlocks() {
        return ContentBlocks.parse(content);
    }

    public String textContent() {
        return contentBlocks().stream()
                .flatMap(block -> block.textValue().stream())
                .reduce("", String::concat);
    }

    public static AgentMessage assistantText(
            String id,
            String parentId,
            Instant timestamp,
            String text,
            JsonNode metadata
    ) {
        Objects.requireNonNull(text, "text");
        return new AgentMessage(
                id,
                parentId,
                timestamp,
                AgentMessageRole.ASSISTANT,
                ContentBlocks.toJsonArray(List.of(new TextBlock(text, null))),
                metadata);
    }
}
