package com.agent4j.ai;

import java.util.Objects;

public record AiSystemMessage(String content) implements AiMessage {
    public AiSystemMessage {
        Objects.requireNonNull(content, "content");
    }

    @Override
    public String role() {
        return "system";
    }
}
