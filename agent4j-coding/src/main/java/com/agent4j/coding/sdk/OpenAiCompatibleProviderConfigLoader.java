package com.agent4j.coding.sdk;

import com.agent4j.ai.AiInputType;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelFeatures;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads OpenAI-compatible provider catalog entries from models.json files. */
public final class OpenAiCompatibleProviderConfigLoader {
    private static final ObjectMapper JSON = new ObjectMapper();

    private OpenAiCompatibleProviderConfigLoader() {
    }

    public static List<OpenAiCompatibleProviderConfig> load(List<Path> files) throws IOException {
        Map<String, OpenAiCompatibleProviderConfig> providers = new LinkedHashMap<>();
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            JsonNode root = JSON.readTree(Files.readString(file));
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("models.json must contain a JSON object: " + file);
            }
            JsonNode definitions = root.path("providers");
            if (!definitions.isObject()) {
                continue;
            }
            definitions.fields().forEachRemaining(entry -> compatibleProvider(entry.getKey(), entry.getValue())
                    .ifPresent(config -> providers.put(config.id(), config)));
        }
        return List.copyOf(providers.values());
    }

    private static Optional<OpenAiCompatibleProviderConfig> compatibleProvider(String id, JsonNode node) {
        if (!"openai-compatible".equals(text(node.path("type")).orElse(null))) {
            return Optional.empty();
        }
        String baseUrl = text(node.path("baseUrl"))
                .orElseThrow(() -> new IllegalArgumentException("openai-compatible provider '" + id + "' requires baseUrl"));
        List<AiModel> models = new ArrayList<>();
        JsonNode configuredModels = node.path("models");
        if (configuredModels.isArray()) {
            configuredModels.forEach(model -> models.add(model(id, model)));
        }
        return Optional.of(new OpenAiCompatibleProviderConfig(
                id,
                text(node.path("name")).orElse(id),
                URI.create(baseUrl),
                text(node.path("apiKeyEnv")),
                text(node.path("baseUrlEnv")),
                headers(node.path("headers")),
                models));
    }

    private static AiModel model(String provider, JsonNode node) {
        String id = text(node.path("id"))
                .orElseThrow(() -> new IllegalArgumentException("openai-compatible provider '" + provider + "' has a model without id"));
        boolean reasoning = bool(node.path("reasoning"), false);
        EnumSet<AiInputType> input = EnumSet.of(AiInputType.TEXT);
        JsonNode inputs = node.path("input");
        if (inputs.isArray()) {
            inputs.forEach(value -> text(value).ifPresent(type -> {
                if ("image".equals(type)) {
                    input.add(AiInputType.IMAGE);
                }
            }));
        }
        AiModelFeatures defaults = AiModelFeatures.defaults(input, reasoning, null);
        JsonNode features = node.path("features");
        AiModelFeatures capabilities = new AiModelFeatures(
                bool(features.path("streaming"), defaults.streaming()),
                bool(features.path("toolCalling"), defaults.toolCalling()),
                bool(features.path("toolChoice"), defaults.toolChoice()),
                bool(features.path("parallelToolCalls"), defaults.parallelToolCalls()),
                bool(features.path("imageInput"), defaults.imageInput()),
                bool(features.path("reasoning"), defaults.reasoning()),
                bool(features.path("promptCaching"), defaults.promptCaching()),
                bool(features.path("systemMessages"), defaults.systemMessages()),
                bool(features.path("developerMessages"), defaults.developerMessages()),
                bool(features.path("structuredOutputs"), defaults.structuredOutputs()));
        return new AiModel(
                new AiModelReference(provider, id),
                text(node.path("name")).orElse(id),
                Optional.of(AiProviderApi.OPENAI_RESPONSES),
                Optional.empty(),
                reasoning,
                Map.of(),
                java.util.Set.of(),
                input,
                positiveLong(node.path("contextWindow"), 128000),
                positiveLong(node.path("maxTokens"), 16384),
                com.agent4j.ai.AiCost.zero(),
                com.agent4j.ai.AiModelCompat.defaults(),
                capabilities);
    }

    private static Map<String, String> headers(JsonNode node) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> text(entry.getValue()).ifPresent(value -> headers.put(entry.getKey(), value)));
        }
        return headers;
    }

    private static long positiveLong(JsonNode node, long fallback) {
        return node.canConvertToLong() && node.asLong() > 0 ? node.asLong() : fallback;
    }

    private static boolean bool(JsonNode node, boolean fallback) {
        return node.isBoolean() ? node.asBoolean() : fallback;
    }

    private static Optional<String> text(JsonNode node) {
        return node.isTextual() ? Optional.of(node.asText().strip()).filter(value -> !value.isEmpty()) : Optional.empty();
    }
}
