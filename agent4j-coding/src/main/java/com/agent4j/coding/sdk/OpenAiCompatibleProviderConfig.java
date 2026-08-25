package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.EnvironmentAiAuthStore;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Catalog data for an OpenAI Responses-compatible endpoint. */
public record OpenAiCompatibleProviderConfig(
        String id,
        String name,
        URI baseUrl,
        Optional<String> apiKeyEnv,
        Optional<String> baseUrlEnv,
        Map<String, String> headers,
        List<AiModel> models
) {
    public OpenAiCompatibleProviderConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(baseUrl, "baseUrl");
        apiKeyEnv = normalize(apiKeyEnv, "apiKeyEnv");
        baseUrlEnv = normalize(baseUrlEnv, "baseUrlEnv");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(models, "models");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        headers = Map.copyOf(headers);
        models = List.copyOf(models);
    }

    public EnvironmentAiAuthStore.ProviderEnvironmentAuth credentials() {
        return new EnvironmentAiAuthStore.ProviderEnvironmentAuth(apiKeyEnv, baseUrlEnv);
    }

    private static Optional<String> normalize(Optional<String> value, String name) {
        Optional<String> optional = value == null ? Optional.empty() : value;
        if (optional.isPresent() && optional.orElseThrow().isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return optional.map(String::strip);
    }
}
