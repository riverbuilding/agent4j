package com.agent4j.ai;

public record AiProviderFeatures(
        boolean streaming,
        boolean requestTimeout,
        boolean requestHeaders,
        boolean requestHooks,
        boolean apiKeyAuth,
        boolean baseUrlOverride,
        boolean usageEvents,
        boolean toolCalling,
        boolean toolChoice,
        boolean parallelToolCalls
) {
    public static AiProviderFeatures defaults() {
        return new AiProviderFeatures(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true);
    }

    public static AiProviderFeatures withoutParallelToolCalls() {
        return new AiProviderFeatures(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false);
    }
}
