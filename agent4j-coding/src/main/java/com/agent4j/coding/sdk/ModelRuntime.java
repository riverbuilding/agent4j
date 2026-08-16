package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderApi;
import com.agent4j.ai.AiProviderFeatures;
import com.agent4j.ai.AiProviderRegistry;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiProviderSelection;
import com.agent4j.ai.AiStreamEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Builds the complete model registry, applies local overlays, and exposes authenticated models. */
public final class ModelRuntime {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final LoginService loginService;
    private final Map<String, BuiltInProviderCatalog.ProviderDefinition> builtIns;
    private final Map<String, List<AiModel>> configuredModels;
    private final Map<String, AiProvider> extensionProviders;
    private final Optional<AiModelReference> configuredDefault;
    private final AiProviderRegistry allModels;

    private ModelRuntime(Builder builder) {
        this.loginService = builder.loginService;
        this.builtIns = new LinkedHashMap<>();
        builder.catalog.providers().forEach(provider -> builtIns.put(provider.id(), provider));
        this.configuredModels = copyModels(builder.configuredModels);
        this.extensionProviders = new LinkedHashMap<>();
        builder.extensionProviders.forEach(provider -> extensionProviders.put(provider.id(), provider));
        this.configuredDefault = builder.configuredDefault;
        this.requestTransformer = builder.requestTransformer;
        this.allModels = registry(configuredDefault.orElseGet(this::firstDefault));
    }

    public static Builder builder(LoginService loginService) {
        return new Builder(loginService);
    }

    /** Compatibility constructor for callers that only need the shipped catalog. */
    public ModelRuntime(LoginService loginService) {
        this(builder(loginService));
    }

    public AiProviderRegistry allModels() {
        return allModels;
    }

    public List<AiProviderSelection> availableModels() {
        return allModels.providers().stream()
                .filter(provider -> loginService.isAuthenticated(provider.id()))
                .flatMap(provider -> provider.models().stream()
                        .map(model -> new AiProviderSelection(provider, model)))
                .toList();
    }

    public AiModelReference resolve(Optional<String> requestedProvider, Optional<String> requestedModel) {
        Objects.requireNonNull(requestedProvider, "requestedProvider");
        Objects.requireNonNull(requestedModel, "requestedModel");
        Optional<AiModelReference> encoded = requestedModel.filter(model -> model.contains("/"))
                .map(ModelRuntime::parseReference);
        if (encoded.isPresent()) {
            AiModelReference reference = encoded.orElseThrow();
            if (requestedProvider.isPresent() && !requestedProvider.orElseThrow().equals(reference.providerId())) {
                throw new IllegalArgumentException("--provider conflicts with the provider encoded in --model");
            }
            return resolveExplicit(reference);
        }
        if (requestedProvider.isPresent()) {
            String provider = requestedProvider.orElseThrow();
            AiModelReference reference = requestedModel
                    .map(model -> new AiModelReference(provider, model))
                    .orElseGet(() -> defaultFor(provider));
            return resolveExplicit(reference);
        }
        if (requestedModel.isPresent()) {
            String model = requestedModel.orElseThrow();
            List<AiProviderSelection> matches = allModels.providers().stream()
                    .flatMap(provider -> provider.models().stream()
                            .filter(candidate -> candidate.id().equals(model))
                            .map(candidate -> new AiProviderSelection(provider, candidate)))
                    .toList();
            if (matches.size() == 1) {
                return matches.getFirst().model().reference();
            }
            if (matches.isEmpty()) {
                throw new IllegalArgumentException("cannot identify a provider for model '" + model
                        + "'; use provider/model or add it to models.json");
            }
            throw new IllegalArgumentException("model '" + model + "' is provided by multiple providers; use provider/model");
        }
        if (configuredDefault.isPresent()) {
            return allModels.require(configuredDefault.orElseThrow()).model().reference();
        }
        List<AiProviderSelection> available = availableModels();
        if (available.isEmpty()) {
            throw new IllegalArgumentException("no authenticated provider; set a provider API key and pass --model");
        }
        List<String> providerIds = available.stream().map(selection -> selection.provider().id()).distinct().toList();
        if (providerIds.size() != 1) {
            throw new IllegalArgumentException("multiple providers are authenticated; pass --model or configure defaultModel");
        }
        return defaultFor(providerIds.getFirst());
    }

    public AiProviderRegistry registry(AiModelReference defaultModel) {
        Objects.requireNonNull(defaultModel, "defaultModel");
        AiProviderRegistry.Builder registry = AiProviderRegistry.builder();
        builtIns.values().forEach(definition -> {
            if (!extensionProviders.containsKey(definition.id())) {
                registry.add(transform(definition.factory().apply(modelsFor(definition, defaultModel))));
            }
        });
        extensionProviders.values().stream().map(this::transform).forEach(registry::add);
        return registry.defaultModel(defaultModel).build();
    }

    private AiModelReference firstDefault() {
        return builtIns.values().stream().findFirst().map(BuiltInProviderCatalog.ProviderDefinition::defaultModel)
                .or(() -> extensionProviders.values().stream()
                        .filter(provider -> !provider.models().isEmpty())
                        .map(provider -> provider.models().getFirst().reference())
                        .findFirst())
                .orElseThrow(() -> new IllegalArgumentException("model runtime has no providers"));
    }

    private AiModelReference defaultFor(String providerId) {
        List<AiModel> models = modelsForProvider(providerId);
        if (models.isEmpty()) {
            throw new IllegalArgumentException("unknown provider: " + providerId);
        }
        return builtIns.getOrDefault(providerId, null) == null
                ? models.getFirst().reference()
                : builtIns.get(providerId).defaultModel();
    }

    private AiModelReference resolveExplicit(AiModelReference reference) {
        if (allModels.resolve(reference).isPresent()) {
            return reference;
        }
        if (builtIns.containsKey(reference.providerId())) {
            return reference;
        }
        throw new IllegalArgumentException("unknown provider/model: " + reference.displayName());
    }

    private List<AiModel> modelsFor(
            BuiltInProviderCatalog.ProviderDefinition definition,
            AiModelReference selectedModel
    ) {
        Map<String, AiModel> models = new LinkedHashMap<>();
        definition.shippedModelIds().forEach(id -> models.put(id, new AiModel(new AiModelReference(definition.id(), id), id)));
        configuredModels.getOrDefault(definition.id(), List.of()).forEach(model -> models.put(model.id(), model));
        if (selectedModel.providerId().equals(definition.id())) {
            models.putIfAbsent(selectedModel.modelId(), new AiModel(selectedModel, selectedModel.modelId()));
        }
        return List.copyOf(models.values());
    }

    private List<AiModel> modelsForProvider(String providerId) {
        AiProvider provider = allModels.provider(providerId).orElseThrow(() -> new IllegalArgumentException(
                "unknown provider: " + providerId));
        return provider.models();
    }

    private static Map<String, List<AiModel>> copyModels(Map<String, List<AiModel>> source) {
        Map<String, List<AiModel>> copy = new LinkedHashMap<>();
        source.forEach((provider, models) -> copy.put(provider, List.copyOf(models)));
        return Map.copyOf(copy);
    }

    private static AiModelReference parseReference(String value) {
        String[] parts = value.split("/", 2);
        if (parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("model must use provider/model form");
        }
        return new AiModelReference(parts[0], parts[1]);
    }

    private AiProvider transform(AiProvider provider) {
        return new RequestTransformingProvider(provider, requestTransformer);
    }

    private final UnaryOperator<AiProviderRequest> requestTransformer;

    private static final class RequestTransformingProvider implements AiProvider {
        private final AiProvider delegate;
        private final UnaryOperator<AiProviderRequest> transformer;

        private RequestTransformingProvider(AiProvider delegate, UnaryOperator<AiProviderRequest> transformer) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.transformer = Objects.requireNonNull(transformer, "transformer");
        }

        @Override public String id() { return delegate.id(); }
        @Override public String name() { return delegate.name(); }
        @Override public AiProviderApi api() { return delegate.api(); }
        @Override public AiProviderFeatures features() { return delegate.features(); }
        @Override public List<AiModel> models() { return delegate.models(); }
        @Override public void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) throws Exception {
            delegate.stream(transformer.apply(request), sink);
        }
    }

    public static final class Builder {
        private final LoginService loginService;
        private BuiltInProviderCatalog catalog = BuiltInProviderCatalog.defaults();
        private final Map<String, List<AiModel>> configuredModels = new LinkedHashMap<>();
        private final List<AiProvider> extensionProviders = new ArrayList<>();
        private Optional<AiModelReference> configuredDefault = Optional.empty();
        private UnaryOperator<AiProviderRequest> requestTransformer = UnaryOperator.identity();

        private Builder(LoginService loginService) {
            this.loginService = Objects.requireNonNull(loginService, "loginService");
        }

        public Builder catalog(BuiltInProviderCatalog catalog) {
            this.catalog = Objects.requireNonNull(catalog, "catalog");
            return this;
        }

        public Builder modelsJson(Path file) throws IOException {
            Objects.requireNonNull(file, "file");
            if (!Files.isRegularFile(file)) {
                return this;
            }
            JsonNode root = JSON.readTree(Files.readString(file));
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("models.json must contain a JSON object: " + file);
            }
            readModels(root.path("models"));
            JsonNode providers = root.path("providers");
            if (providers.isObject()) {
                providers.fields().forEachRemaining(entry -> readProviderModels(entry.getKey(), entry.getValue().path("models")));
            }
            text(root.path("defaultModel")).map(ModelRuntime::parseReference).ifPresent(reference -> configuredDefault = Optional.of(reference));
            return this;
        }

        public Builder extensionProvider(AiProvider provider) {
            extensionProviders.add(Objects.requireNonNull(provider, "provider"));
            return this;
        }

        public Builder extensionProviders(List<? extends AiProvider> providers) {
            Objects.requireNonNull(providers, "providers").forEach(this::extensionProvider);
            return this;
        }

        public Builder providerRequestTransformer(UnaryOperator<AiProviderRequest> transformer) {
            this.requestTransformer = Objects.requireNonNull(transformer, "transformer");
            return this;
        }

        public ModelRuntime build() {
            return new ModelRuntime(this);
        }

        private void readModels(JsonNode models) {
            if (!models.isArray()) {
                return;
            }
            models.forEach(model -> {
                Optional<String> provider = text(model.path("provider"));
                Optional<String> id = text(model.path("id"));
                if (provider.isPresent() && id.isPresent()) {
                    addModel(provider.orElseThrow(), id.orElseThrow(), text(model.path("name")).orElse(id.orElseThrow()));
                }
            });
        }

        private void readProviderModels(String provider, JsonNode models) {
            if (!models.isArray()) {
                return;
            }
            models.forEach(model -> text(model.path("id"))
                    .ifPresent(id -> addModel(provider, id, text(model.path("name")).orElse(id))));
        }

        private void addModel(String provider, String id, String name) {
            configuredModels.computeIfAbsent(provider, ignored -> new ArrayList<>())
                    .add(new AiModel(new AiModelReference(provider, id), name));
        }

        private static Optional<String> text(JsonNode node) {
            return node.isTextual() ? Optional.of(node.asText().strip()).filter(value -> !value.isEmpty()) : Optional.empty();
        }
    }
}
