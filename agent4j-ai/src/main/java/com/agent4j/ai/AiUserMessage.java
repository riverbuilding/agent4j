package com.agent4j.ai;

import java.util.List;
import java.util.Objects;

public record AiUserMessage(List<AiContentBlock> content) implements AiMessage {
    public AiUserMessage {
        Objects.requireNonNull(content, "content");
        content = List.copyOf(content);
    }

    public static AiUserMessage text(String text) {
        return new AiUserMessage(List.of(new AiTextContent(text)));
    }

    @Override
    public String role() {
        return "user";
    }
}
