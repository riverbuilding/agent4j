package com.agent4j.core.message;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.Optional;

public record CustomAgentMessageView(AgentMessage envelope) implements AgentMessageView {
    public CustomAgentMessageView {
        Objects.requireNonNull(envelope, "envelope");
    }

    public String text() {
        if (envelope.content() == null || envelope.content().isNull()) {
            return "";
        }
        if (envelope.content().isTextual()) {
            return envelope.content().asText();
        }
        String text = envelope.textContent();
        return text.isBlank() && !envelope.content().isArray() ? envelope.content().toString() : text;
    }

    public Optional<String> customType() {
        return textField("customType");
    }

    public Optional<JsonNode> field(String fieldName) {
        if (envelope.metadata() == null || !envelope.metadata().has(fieldName)) {
            if (envelope.content() != null && envelope.content().isObject() && envelope.content().has(fieldName)) {
                return Optional.of(envelope.content().get(fieldName));
            }
            return Optional.empty();
        }
        return Optional.of(envelope.metadata().get(fieldName));
    }

    public Optional<String> textField(String fieldName) {
        return field(fieldName)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText);
    }

    public Optional<Integer> intField(String fieldName) {
        return field(fieldName)
                .filter(JsonNode::canConvertToInt)
                .map(JsonNode::asInt);
    }
}
