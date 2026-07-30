package com.agent4j.coding.message;

import com.agent4j.core.message.AgentMessage;
import com.fasterxml.jackson.databind.JsonNode;

final class CodingAgentMessages {
    private CodingAgentMessages() {
    }

    static String textContent(AgentMessage message) {
        if (message.content() == null || message.content().isNull()) {
            return "";
        }
        if (message.content().isTextual()) {
            return message.content().asText();
        }
        String text = message.textContent();
        return text.isBlank() && !message.content().isArray() ? message.content().toString() : text;
    }

    static String textField(AgentMessage message, String fieldName) {
        JsonNode field = field(message, fieldName);
        return field != null && field.isTextual() ? field.asText() : null;
    }

    static Integer intField(AgentMessage message, String fieldName) {
        JsonNode field = field(message, fieldName);
        return field != null && field.canConvertToInt() ? field.asInt() : null;
    }

    static JsonNode field(AgentMessage message, String fieldName) {
        if (message.metadata() != null && message.metadata().has(fieldName)) {
            return message.metadata().get(fieldName);
        }
        if (message.content() != null && message.content().isObject() && message.content().has(fieldName)) {
            return message.content().get(fieldName);
        }
        return null;
    }
}
