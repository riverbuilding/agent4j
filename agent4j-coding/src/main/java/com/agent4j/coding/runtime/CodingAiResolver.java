package com.agent4j.coding.runtime;

import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderRegistry;
import com.agent4j.ai.AiProviderSelection;
import com.agent4j.ai.AiResolvedAuth;
import com.agent4j.coding.resource.AgentSettings;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CodingAiResolver {
    private final AiProviderRegistry registry;

    public CodingAiResolver(AiProviderRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public AiProviderSelection resolveSelection(AgentSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return modelReference(settings)
                .map(registry::require)
                .orElseGet(registry::requireDefault);
    }

    public AiResolvedAuth resolveAuth(AgentSettings settings, String providerId) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(providerId, "providerId");
        JsonNode provider = settings.values().at("/providers/" + providerId);
        if (!provider.isObject()) {
            return AiResolvedAuth.none();
        }
        Optional<String> apiKey = text(provider.get("apiKey"));
        Optional<String> baseUrl = text(provider.get("baseUrl"));
        Map<String, String> headers = stringMap(provider.get("headers"));
        if (apiKey.isEmpty() && baseUrl.isEmpty() && headers.isEmpty()) {
            return AiResolvedAuth.none();
        }
        return new AiResolvedAuth(apiKey, headers, baseUrl, Optional.of("settings"), Map.of());
    }

    private Optional<AiModelReference> modelReference(AgentSettings settings) {
        Optional<String> providerId = settings.textField("defaultProvider");
        Optional<String> modelId = settings.textField("defaultModel")
                .or(() -> providerId.flatMap(provider -> text(settings.values().at("/models/" + provider + "/default"))));
        if (providerId.isPresent() && modelId.isPresent()) {
            return Optional.of(new AiModelReference(providerId.orElseThrow(), modelId.orElseThrow()));
        }
        return Optional.empty();
    }

    private static Map<String, String> stringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(field -> text(field.getValue()).ifPresent(value -> values.put(field.getKey(), value)));
        return Map.copyOf(values);
    }

    private static Optional<String> text(JsonNode node) {
        return Optional.ofNullable(node)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .filter(value -> !value.isBlank());
    }
}
