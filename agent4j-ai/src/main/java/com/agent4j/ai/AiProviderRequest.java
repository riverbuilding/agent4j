package com.agent4j.ai;

import java.util.Objects;

public record AiProviderRequest(
        AiModel model,
        AiTurnRequest turn,
        AiProviderContext context,
        AiStreamOptions options
) {
    public AiProviderRequest {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(turn, "turn");
        Objects.requireNonNull(context, "context");
        model = AiEndpointResolver.applyAuthBaseUrl(model, context.auth());
        options = options == null ? AiStreamOptions.defaults() : options;
    }
}
