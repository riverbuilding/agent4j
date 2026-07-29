package com.agent4j.ai;

import java.util.List;
import java.util.Objects;

public record AiToolResultMessage(
        String toolCallId,
        String toolName,
        List<AiContentBlock> content,
        boolean error,
        AiUsage usage
) implements AiMessage {
    public AiToolResultMessage {
        Objects.requireNonNull(toolCallId, "toolCallId");
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(content, "content");
        content = List.copyOf(content);
    }

    public AiToolResultMessage(String toolCallId, String toolName, List<AiContentBlock> content, boolean error) {
        this(toolCallId, toolName, content, error, null);
    }

    @Override
    public String role() {
        return "toolResult";
    }
}
