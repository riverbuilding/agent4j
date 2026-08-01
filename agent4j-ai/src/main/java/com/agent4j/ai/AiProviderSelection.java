package com.agent4j.ai;

import java.util.Objects;

public record AiProviderSelection(AiProvider provider, AiModel model) {
    public AiProviderSelection {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
    }
}
