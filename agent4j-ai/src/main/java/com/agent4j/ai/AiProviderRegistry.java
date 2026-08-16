package com.agent4j.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AiProviderRegistry {
    private final Map<String, AiProvider> providers;
    private final Optional<AiModelReference> defaultModel;

    private AiProviderRegistry(Map<String, AiProvider> providers, Optional<AiModelReference> defaultModel) {
        this.providers = Collections.unmodifiableMap(new LinkedHashMap<>(providers));
        this.defaultModel = Objects.requireNonNull(defaultModel, "defaultModel");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AiProviderRegistry fixedClient(AiModel model, AiModelClient client) {
        Objects.requireNonNull(model, "model");
        return builder()
                .add(new AiModelClientProvider(model, client))
                .defaultModel(model.reference())
                .build();
    }

    public List<AiProvider> providers() {
        return List.copyOf(providers.values());
    }

    public Optional<AiProvider> provider(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return Optional.ofNullable(providers.get(providerId));
    }

    public Optional<AiProviderSelection> resolveDefault() {
        return defaultModel.flatMap(this::resolve);
    }

    public AiProviderSelection requireDefault() {
        return resolveDefault()
                .orElseThrow(() -> new IllegalArgumentException("default model is not configured"));
    }

    public Optional<AiProviderSelection> resolve(AiModelReference reference) {
        Objects.requireNonNull(reference, "reference");
        return provider(reference.providerId())
                .flatMap(provider -> provider.model(reference.modelId())
                        .map(model -> new AiProviderSelection(provider, model)));
    }

    public AiProviderSelection require(AiModelReference reference) {
        return resolve(reference)
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown provider/model: " + reference.providerId() + "/" + reference.modelId()));
    }

    public Optional<AiProviderSelection> resolve(String providerId, String modelId) {
        return resolve(new AiModelReference(providerId, modelId));
    }

    public static final class Builder {
        private final Map<String, AiProvider> providers = new LinkedHashMap<>();
        private AiModelReference defaultModel;

        public Builder add(AiProvider provider) {
            Objects.requireNonNull(provider, "provider");
            if (providers.containsKey(provider.id())) {
                throw new IllegalArgumentException("duplicate provider id: " + provider.id());
            }
            providers.put(provider.id(), provider);
            return this;
        }

        public Builder addAll(List<? extends AiProvider> providers) {
            Objects.requireNonNull(providers, "providers");
            providers.forEach(this::add);
            return this;
        }

        public Builder defaultModel(AiModelReference reference) {
            this.defaultModel = Objects.requireNonNull(reference, "reference");
            return this;
        }

        public AiProviderRegistry build() {
            Map<String, AiProvider> copy = new LinkedHashMap<>(providers);
            if (copy.isEmpty()) {
                throw new IllegalArgumentException("provider registry must contain at least one provider");
            }
            Optional<AiModelReference> defaultReference = Optional.ofNullable(defaultModel)
                    .or(() -> firstProviderModel(copy));
            AiProviderRegistry registry = new AiProviderRegistry(copy, defaultReference);
            defaultReference.ifPresent(registry::require);
            return registry;
        }

        private static Optional<AiModelReference> firstProviderModel(Map<String, AiProvider> providers) {
            List<AiProvider> ordered = new ArrayList<>(providers.values());
            for (AiProvider provider : ordered) {
                if (!provider.models().isEmpty()) {
                    AiModel model = provider.models().getFirst();
                    return Optional.of(new AiModelReference(provider.id(), model.id()));
                }
            }
            return Optional.empty();
        }
    }
}
