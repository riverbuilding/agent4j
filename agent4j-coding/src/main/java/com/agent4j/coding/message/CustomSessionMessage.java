package com.agent4j.coding.message;

import com.agent4j.core.message.CustomAgentMessageView;

import java.util.Objects;

public record CustomSessionMessage(String customType, String content) {
    public CustomSessionMessage {
        customType = customType == null ? "custom" : customType;
        content = content == null ? "" : content;
    }

    public static CustomSessionMessage from(CustomAgentMessageView message) {
        Objects.requireNonNull(message, "message");
        String customType = message.customType().orElse(null);
        return new CustomSessionMessage(customType, message.text());
    }

    public String toLlmText() {
        return "<customMessage type=\"" + PromptMarkup.attribute(customType) + "\">\n" + content + "\n</customMessage>";
    }
}
