package com.agent4j.coding.session;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Optional;

public record SessionHeader(
        String id,
        int version,
        Instant timestamp,
        String cwd,
        JsonNode payload
) {
    public Optional<String> sourceSessionId() {
        JsonNode value = payload.get("sourceSessionId");
        return value != null && value.isTextual() ? Optional.of(value.asText()) : Optional.empty();
    }

    public Optional<String> forkedFromEntryId() {
        JsonNode value = payload.get("forkedFromEntryId");
        return value != null && value.isTextual() ? Optional.of(value.asText()) : Optional.empty();
    }
}
