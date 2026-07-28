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
    public Optional<SessionHeader> header() {
        if (type() != SessionEntryType.SESSION) {
            return Optional.empty();
        }
        JsonNode version = payload.get("version");
        JsonNode cwd = payload.get("cwd");
        return Optional.of(new SessionHeader(
                id,
                version != null && version.canConvertToInt() ? version.asInt() : -1,
                timestamp,
                cwd != null && cwd.isTextual() ? cwd.asText() : null,
                payload));
    }

    @JsonIgnore
    public Optional<SessionMessage> message() {
        if (type() != SessionEntryType.MESSAGE) {
            return Optional.empty();
        }
        JsonNode message = payload.get("message");
        if (message == null || message.isNull()) {
            return Optional.empty();
        }
        JsonNode role = message.get("role");
        JsonNode content = message.get("content");
        return Optional.of(new SessionMessage(
                role != null && role.isTextual() ? SessionMessageRole.fromWireName(role.asText()) : SessionMessageRole.UNKNOWN,
                content,
                message));
    }

    @JsonIgnore
    public Optional<SessionMessageRole> messageRole() {
        return message().map(SessionMessage::role);
    }

    @JsonIgnore
    public Optional<SessionModelChange> modelChange() {
        if (type() != SessionEntryType.MODEL_CHANGE) {
            return Optional.empty();
        }
        return Optional.of(new SessionModelChange(textOrNull("provider"), textOrNull("modelId"), payload));
    }

    @JsonIgnore
    public Optional<SessionThinkingLevelChange> thinkingLevelChange() {
        if (type() != SessionEntryType.THINKING_LEVEL_CHANGE) {
            return Optional.empty();
        }
        return Optional.of(new SessionThinkingLevelChange(textOrNull("thinkingLevel"), payload));
    }

    @JsonIgnore
    public Optional<SessionCompaction> compaction() {
        if (type() != SessionEntryType.COMPACTION) {
            return Optional.empty();
        }
        return Optional.of(new SessionCompaction(payload.get("summary"), payload.get("retainedEntries"), payload));
    }

    @JsonIgnore
    public Optional<SessionInfo> sessionInfo() {
        if (type() != SessionEntryType.SESSION_INFO) {
            return Optional.empty();
        }
        return Optional.of(new SessionInfo(textOrNull("name"), payload));
    }

    @JsonIgnore
    public Optional<SessionFileEntry> fileEntry() {
        if (type() != SessionEntryType.FILE) {
            return Optional.empty();
        }
        return Optional.of(new SessionFileEntry(textOrNull("path"), payload));
    }

    @JsonIgnore
    public Optional<SessionCustomEntry> customEntry() {
        if (type() != SessionEntryType.CUSTOM) {
            return Optional.empty();
        }
        return Optional.of(new SessionCustomEntry(textOrNull("customType"), payload));
    }

    @JsonIgnore
    public Optional<String> textField(String fieldName) {
        return Optional.ofNullable(textOrNull(fieldName));
    }

    private String textOrNull(String fieldName) {
        JsonNode value = payload.get(fieldName);
        return value != null && value.isTextual() ? value.asText() : null;
    }
}
