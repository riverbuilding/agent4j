package com.agent4j.ai;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record AiModel(
        AiModelReference reference,
        String name,
        Optional<AiProviderApi> api,
        Optional<String> baseUrl,
        boolean reasoning,
        Map<AiThinkingLevel, String> thinkingLevelMap,
        Set<AiThinkingLevel> unsupportedThinkingLevels,
        Set<AiInputType> input,
        long contextWindow,
        long maxTokens,
        AiCost cost,
        AiModelCompat compat,
        AiModelFeatures features
) {
    public AiModel {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(thinkingLevelMap, "thinkingLevelMap");
        Objects.requireNonNull(unsupportedThinkingLevels, "unsupportedThinkingLevels");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(compat, "compat");
        features = features == null ? AiModelFeatures.defaults(input, reasoning, compat) : features;
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (contextWindow <= 0) {
            throw new IllegalArgumentException("contextWindow must be positive");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        thinkingLevelMap = Map.copyOf(thinkingLevelMap);
        unsupportedThinkingLevels = Set.copyOf(unsupportedThinkingLevels);
        input = input.isEmpty() ? Set.of(AiInputType.TEXT) : Set.copyOf(input);
    }

    public AiModel(AiModelReference reference, String name) {
        this(
                reference,
                name,
                Optional.empty(),
                Optional.empty(),
                false,
                Map.of(),
                Set.of(),
                EnumSet.of(AiInputType.TEXT),
                128000,
                16384,
                AiCost.zero(),
                AiModelCompat.defaults(),
                null);
    }

    public AiModel(
            AiModelReference reference,
            String name,
            Optional<AiProviderApi> api,
            Optional<String> baseUrl,
            boolean reasoning,
            Map<AiThinkingLevel, String> thinkingLevelMap,
            Set<AiThinkingLevel> unsupportedThinkingLevels,
            Set<AiInputType> input,
            long contextWindow,
            long maxTokens,
            AiCost cost,
            AiModelCompat compat
    ) {
        this(
                reference,
                name,
                api,
                baseUrl,
                reasoning,
                thinkingLevelMap,
                unsupportedThinkingLevels,
                input,
                contextWindow,
                maxTokens,
                cost,
                compat,
                null);
    }

    public String providerId() {
        return reference.providerId();
    }

    public String id() {
        return reference.modelId();
    }
}
