package com.agent4j.ai.openai;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiProviderFeatures;
import com.agent4j.ai.AiProviderRequestHook;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record OpenAiResponsesProviderOptions(
        String id,
        String name,
        URI endpoint,
        List<AiModel> models,
        Map<String, String> defaultHeaders,
        AiProviderRequestHook requestHook,
        AiProviderFeatures features
) {
    public OpenAiResponsesProviderOptions {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(defaultHeaders, "defaultHeaders");
        requestHook = requestHook == null ? AiProviderRequestHook.identity() : requestHook;
        features = features == null ? AiProviderFeatures.defaults() : features;
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        models = List.copyOf(models);
        defaultHeaders = Map.copyOf(defaultHeaders);
    }

    public static OpenAiResponsesProviderOptions defaults(List<AiModel> models) {
        return new OpenAiResponsesProviderOptions(
                "openai",
                "OpenAI",
                URI.create("https://api.openai.com/v1/responses"),
                models,
                Map.of(),
                AiProviderRequestHook.identity(),
                AiProviderFeatures.defaults());
    }

    public OpenAiResponsesProviderOptions(
            String id,
            String name,
            URI endpoint,
            List<AiModel> models,
            Map<String, String> defaultHeaders,
            AiProviderRequestHook requestHook
    ) {
        this(id, name, endpoint, models, defaultHeaders, requestHook, AiProviderFeatures.defaults());
    }
}
