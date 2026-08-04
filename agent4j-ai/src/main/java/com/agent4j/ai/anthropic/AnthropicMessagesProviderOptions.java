package com.agent4j.ai.anthropic;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiProviderFeatures;
import com.agent4j.ai.AiProviderRequestHook;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AnthropicMessagesProviderOptions(
        String id,
        String name,
        URI endpoint,
        List<AiModel> models,
        Map<String, String> defaultHeaders,
        AiProviderRequestHook requestHook,
        String anthropicVersion,
        AiProviderFeatures features
) {
    public AnthropicMessagesProviderOptions {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(defaultHeaders, "defaultHeaders");
        requestHook = requestHook == null ? AiProviderRequestHook.identity() : requestHook;
        anthropicVersion = anthropicVersion == null || anthropicVersion.isBlank() ? "2023-06-01" : anthropicVersion;
        features = features == null ? AiProviderFeatures.withoutParallelToolCalls() : features;
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        models = List.copyOf(models);
        defaultHeaders = Map.copyOf(defaultHeaders);
    }

    public static AnthropicMessagesProviderOptions defaults(List<AiModel> models) {
        return new AnthropicMessagesProviderOptions(
                "anthropic",
                "Anthropic",
                URI.create("https://api.anthropic.com/v1/messages"),
                models,
                Map.of(),
                AiProviderRequestHook.identity(),
                "2023-06-01",
                AiProviderFeatures.withoutParallelToolCalls());
    }

    public AnthropicMessagesProviderOptions(
            String id,
            String name,
            URI endpoint,
            List<AiModel> models,
            Map<String, String> defaultHeaders,
            AiProviderRequestHook requestHook,
            String anthropicVersion
    ) {
        this(
                id,
                name,
                endpoint,
                models,
                defaultHeaders,
                requestHook,
                anthropicVersion,
                AiProviderFeatures.withoutParallelToolCalls());
    }
}
