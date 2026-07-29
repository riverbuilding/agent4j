package com.agent4j.ai;

import java.util.Objects;

public record AiTextContent(String text) implements AiContentBlock {
    public AiTextContent {
        Objects.requireNonNull(text, "text");
    }

    @Override
    public String type() {
        return "text";
    }
}
