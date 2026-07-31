package com.agent4j.ai;

import java.util.Objects;

public record AiModelReference(String providerId, String modelId) {
    public AiModelReference {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        if (providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
    }

    public String displayName() {
        return providerId + "/" + modelId;
    }
}
