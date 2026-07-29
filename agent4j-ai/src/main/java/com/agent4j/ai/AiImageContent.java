package com.agent4j.ai;

import java.util.Objects;

public record AiImageContent(String data, String mimeType) implements AiContentBlock {
    public AiImageContent {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(mimeType, "mimeType");
    }

    @Override
    public String type() {
        return "image";
    }
}
