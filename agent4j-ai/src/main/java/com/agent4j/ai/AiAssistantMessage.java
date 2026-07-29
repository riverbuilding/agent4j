package com.agent4j.ai;

import java.util.List;
import java.util.Objects;

public record AiAssistantMessage(
        List<AiContentBlock> content,
        AiStopReason stopReason,
        AiUsage usage,
        String errorMessage
) implements AiMessage {
    public AiAssistantMessage {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(stopReason, "stopReason");
        usage = usage == null ? AiUsage.zero() : usage;
        content = List.copyOf(content);
    }

    public AiAssistantMessage(List<AiContentBlock> content, AiStopReason stopReason, AiUsage usage) {
        this(content, stopReason, usage, null);
    }

    @Override
    public String role() {
        return "assistant";
    }
}
