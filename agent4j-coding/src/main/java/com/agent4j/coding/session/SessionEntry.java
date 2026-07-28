package com.agent4j.coding.session;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record SessionEntry(
        @JsonProperty("type") String rawType,
        @JsonProperty("id") String id,
        @JsonProperty("parentId") String parentId,
        @JsonProperty("timestamp") Instant timestamp,
        JsonNode payload
) {
    public SessionEntry {
        Objects.requireNonNull(rawType, "rawType");
        Objects.requireNonNull(payload, "payload");
    }

    @JsonIgnore
    public SessionEntryType type() {
        return SessionEntryType.fromWireName(rawType);
    }

    @JsonIgnore
    public boolean isHeader() {
        return type() == SessionEntryType.SESSION;
    }

    @JsonIgnore
    public Optional<String> optionalId() {
        return Optional.ofNullable(id);
    }

    @JsonIgnore
    public Optional<String> optionalParentId() {
        return Optional.ofNullable(parentId);
    }

    @JsonIgnore
    public Optional<JsonNode> message() {
        if (type() != SessionEntryType.MESSAGE) {
            return Optional.empty();
        }
        JsonNode message = payload.get("message");
        return message == null || message.isNull() ? Optional.empty() : Optional.of(message);
    }

    @JsonIgnore
    public Optional<SessionMessageRole> messageRole() {
        return message()
                .map(message -> message.get("role"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .map(SessionMessageRole::fromWireName);
    }

    @JsonIgnore
    public Optional<String> textField(String fieldName) {
        JsonNode value = payload.get(fieldName);
        return value != null && value.isTextual() ? Optional.of(value.asText()) : Optional.empty();
    }
}
