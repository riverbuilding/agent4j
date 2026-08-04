package com.agent4j.ai;

import java.util.Set;

public record AiModelFeatures(
        boolean streaming,
        boolean toolCalling,
        boolean toolChoice,
        boolean parallelToolCalls,
        boolean imageInput,
        boolean reasoning,
        boolean promptCaching,
        boolean systemMessages,
        boolean developerMessages,
        boolean structuredOutputs
) {
    public static AiModelFeatures defaults() {
        return new AiModelFeatures(
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                true,
                false);
    }

    public static AiModelFeatures defaults(
            Set<AiInputType> input,
            boolean reasoning,
            AiModelCompat compat
    ) {
        return new AiModelFeatures(
                true,
                true,
                true,
                true,
                input != null && input.contains(AiInputType.IMAGE),
                reasoning,
                compat != null && compat.cacheControlFormat().isPresent(),
                true,
                compat == null || compat.supportsDeveloperRole(),
                false);
    }
}
