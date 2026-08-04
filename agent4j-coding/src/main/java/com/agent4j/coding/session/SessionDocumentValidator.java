package com.agent4j.coding.session;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public final class SessionDocumentValidator {
    private SessionDocumentValidator() {
    }

    public static void validate(SessionDocument document) {
        Objects.requireNonNull(document, "document");
        validateHeader(document.header());
        for (SessionEntry entry : document.entries()) {
            validateEntry(entry);
        }
        SessionTree.from(document);
    }

    private static void validateHeader(SessionEntry header) {
        requireText(header, "id");
        requirePresent(header, "timestamp");
        requireInt(header, "version");
        requireText(header, "cwd");
    }

    private static void validateEntry(SessionEntry entry) {
        requireText(entry, "id");
        requirePresent(entry, "timestamp");
        switch (entry.type()) {
            case MESSAGE -> validateMessage(entry);
            case MODEL_CHANGE -> {
                requireText(entry, "provider");
                requireText(entry, "modelId");
            }
            case THINKING_LEVEL_CHANGE -> requireText(entry, "thinkingLevel");
            case COMPACTION -> {
                requireObject(entry, "summary");
                requireArray(entry, "retainedEntries");
            }
            case FILE -> requireText(entry, "path");
            case CUSTOM -> requireText(entry, "customType");
            case SESSION, SESSION_INFO, UNKNOWN -> {
            }
        }
    }

    private static void validateMessage(SessionEntry entry) {
        JsonNode message = requireObject(entry, "message");
        requireText(entry, message, "message.role");
        SessionMessageRole role = SessionMessageRole.fromWireName(message.get("role").asText());
        switch (role) {
            case USER, ASSISTANT, TOOL_RESULT, BRANCH_SUMMARY, COMPACTION_SUMMARY ->
                    requirePresent(entry, message, "message.content");
            case BASH_EXECUTION -> requireText(entry, message, "message.command");
            case CUSTOM -> requireText(entry, message, "message.customType");
            case UNKNOWN -> {
            }
        }
        if (role == SessionMessageRole.TOOL_RESULT) {
            requireText(entry, message, "message.toolCallId");
        }
    }

    private static void requireText(SessionEntry entry, String fieldName) {
        JsonNode value = entry.payload().get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw malformed(entry, fieldName + " must be a non-empty string");
        }
    }

    private static void requireText(SessionEntry entry, JsonNode node, String path) {
        JsonNode value = node.get(path.substring(path.lastIndexOf('.') + 1));
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw malformed(entry, path + " must be a non-empty string");
        }
    }

    private static void requirePresent(SessionEntry entry, String fieldName) {
        JsonNode value = entry.payload().get(fieldName);
        if (value == null || value.isNull()) {
            throw malformed(entry, fieldName + " is required");
        }
    }

    private static void requirePresent(SessionEntry entry, JsonNode node, String path) {
        JsonNode value = node.get(path.substring(path.lastIndexOf('.') + 1));
        if (value == null || value.isNull()) {
            throw malformed(entry, path + " is required");
        }
    }

    private static void requireInt(SessionEntry entry, String fieldName) {
        JsonNode value = entry.payload().get(fieldName);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw malformed(entry, fieldName + " must be an integer");
        }
    }

    private static JsonNode requireObject(SessionEntry entry, String fieldName) {
        JsonNode value = entry.payload().get(fieldName);
        if (value == null || !value.isObject()) {
            throw malformed(entry, fieldName + " must be an object");
        }
        return value;
    }

    private static void requireArray(SessionEntry entry, String fieldName) {
        JsonNode value = entry.payload().get(fieldName);
        if (value == null || !value.isArray()) {
            throw malformed(entry, fieldName + " must be an array");
        }
    }

    private static IllegalArgumentException malformed(SessionEntry entry, String message) {
        return new IllegalArgumentException("malformed " + entry.rawType() + " session entry "
                + entry.id() + ": " + message);
    }
}
