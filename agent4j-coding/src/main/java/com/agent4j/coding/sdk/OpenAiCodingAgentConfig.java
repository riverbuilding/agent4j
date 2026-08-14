package com.agent4j.coding.sdk;

import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.ToolRegistry;

import java.util.Objects;
import java.util.Optional;

/** Configuration for a coding agent backed by the OpenAI Responses API. */
public record OpenAiCodingAgentConfig(
        String apiKey,
        String model,
        Optional<String> baseUrl,
        Optional<Integer> maxOutputTokens,
        ToolRegistry toolRegistry
) {
    public OpenAiCodingAgentConfig {
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(model, "model");
        baseUrl = baseUrl == null ? Optional.empty() : baseUrl;
        maxOutputTokens = maxOutputTokens == null ? Optional.empty() : maxOutputTokens;
        Objects.requireNonNull(toolRegistry, "toolRegistry");
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        if (model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        baseUrl.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("baseUrl must not be blank");
            }
        });
        maxOutputTokens.ifPresent(value -> {
            if (value <= 0) {
                throw new IllegalArgumentException("maxOutputTokens must be positive");
            }
        });
    }

    public static Builder builder(String apiKey, String model) {
        return new Builder(apiKey, model);
    }

    public static final class Builder {
        private final String apiKey;
        private final String model;
        private Optional<String> baseUrl = Optional.empty();
        private Optional<Integer> maxOutputTokens = Optional.empty();
        private ToolRegistry toolRegistry = InMemoryToolRegistry.builder().build();

        private Builder(String apiKey, String model) {
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
            this.model = Objects.requireNonNull(model, "model");
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Optional.of(Objects.requireNonNull(baseUrl, "baseUrl"));
            return this;
        }

        public Builder maxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = Optional.of(maxOutputTokens);
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
            return this;
        }

        public OpenAiCodingAgentConfig build() {
            return new OpenAiCodingAgentConfig(apiKey, model, baseUrl, maxOutputTokens, toolRegistry);
        }
    }
}
