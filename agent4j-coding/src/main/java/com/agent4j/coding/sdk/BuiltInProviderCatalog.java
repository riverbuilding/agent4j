package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.EnvironmentAiAuthStore;
import com.agent4j.ai.anthropic.AnthropicMessagesProvider;
import com.agent4j.ai.openai.OpenAiResponsesProvider;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Shipped providers, their stable identifiers, model catalog, and environment credentials. */
public final class BuiltInProviderCatalog {
    private final List<ProviderDefinition> providers;

    private BuiltInProviderCatalog(List<ProviderDefinition> providers) {
        this.providers = List.copyOf(providers);
    }

    public static BuiltInProviderCatalog defaults() {
        return new BuiltInProviderCatalog(List.of(
                new ProviderDefinition(
                        "openai",
                        new AiModelReference("openai", "gpt-5"),
                        List.of("gpt-5", "gpt-5-mini", "gpt-4.1"),
                        new EnvironmentAiAuthStore.ProviderEnvironmentAuth(
                                java.util.Optional.of("OPENAI_API_KEY"), java.util.Optional.of("OPENAI_BASE_URL")),
                        OpenAiResponsesProvider::new),
                new ProviderDefinition(
                        "anthropic",
                        new AiModelReference("anthropic", "claude-sonnet-4-5"),
                        List.of("claude-sonnet-4-5", "claude-opus-4-5", "claude-haiku-4-5"),
                        new EnvironmentAiAuthStore.ProviderEnvironmentAuth(
                                java.util.Optional.of("ANTHROPIC_API_KEY"), java.util.Optional.of("ANTHROPIC_BASE_URL")),
                        AnthropicMessagesProvider::new)));
    }

    public List<ProviderDefinition> providers() {
        return providers;
    }

    public Map<String, EnvironmentAiAuthStore.ProviderEnvironmentAuth> credentialDescriptors() {
        Map<String, EnvironmentAiAuthStore.ProviderEnvironmentAuth> descriptors = new LinkedHashMap<>();
        providers.forEach(provider -> descriptors.put(provider.id(), provider.credentials()));
        return Map.copyOf(descriptors);
    }

    public EnvironmentAiAuthStore environmentAuthStore() {
        return new EnvironmentAiAuthStore(System.getenv(), credentialDescriptors());
    }

    public record ProviderDefinition(
            String id,
            AiModelReference defaultModel,
            List<String> shippedModelIds,
            EnvironmentAiAuthStore.ProviderEnvironmentAuth credentials,
            Function<List<AiModel>, AiProvider> factory
    ) {
        public ProviderDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(defaultModel, "defaultModel");
            Objects.requireNonNull(shippedModelIds, "shippedModelIds");
            Objects.requireNonNull(credentials, "credentials");
            Objects.requireNonNull(factory, "factory");
            if (id.isBlank() || !id.equals(defaultModel.providerId())) {
                throw new IllegalArgumentException("provider id must match its default model");
            }
            shippedModelIds = List.copyOf(shippedModelIds);
        }
    }
}
