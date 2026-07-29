package com.agent4j.ai;

import java.util.Objects;

public record AiThinkingContent(String thinking, String thinkingSignature, boolean redacted) implements AiContentBlock {
    public AiThinkingContent {
        Objects.requireNonNull(thinking, "thinking");
    }

    public AiThinkingContent(String thinking) {
        this(thinking, null, false);
    }

    @Override
    public String type() {
        return "thinking";
    }
}
