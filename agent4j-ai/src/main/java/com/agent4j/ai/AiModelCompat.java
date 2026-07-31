package com.agent4j.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.Objects;
import java.util.Optional;

public record AiModelCompat(
        boolean supportsDeveloperRole,
        boolean supportsReasoningEffort,
        Optional<String> maxTokensField,
        Optional<String> thinkingFormat,
        Optional<String> cacheControlFormat,
        boolean requiresToolResultName,
        boolean forceAdaptiveThinking,
        boolean allowEmptySignature,
        JsonNode providerOptions
) {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    public AiModelCompat {
        Objects.requireNonNull(maxTokensField, "maxTokensField");
        Objects.requireNonNull(thinkingFormat, "thinkingFormat");
        Objects.requireNonNull(cacheControlFormat, "cacheControlFormat");
        providerOptions = providerOptions == null ? JSON.objectNode() : providerOptions.deepCopy();
    }

    public static AiModelCompat defaults() {
        return new AiModelCompat(
                true,
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                false,
                false,
                JSON.objectNode());
    }
}
