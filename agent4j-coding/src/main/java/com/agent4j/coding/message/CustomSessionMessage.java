package com.agent4j.coding.message;

import com.agent4j.core.message.AgentMessage;

import java.util.Objects;

public record CustomSessionMessage(String customType, String content) {
    public CustomSessionMessage {
        customType = customType == null ? "custom" : customType;
        content = content == null ? "" : content;
    }

    public static CustomSessionMessage from(AgentMessage message) {
        Objects.requireNonNull(message, "message");
        String customType = CodingAgentMessages.textField(message, "customType");
        return new CustomSessionMessage(customType, CodingAgentMessages.textContent(message));
    }

    public String toLlmText() {
        return "<customMessage type=\"" + customType + "\">\n" + content + "\n</customMessage>";
    }
}
