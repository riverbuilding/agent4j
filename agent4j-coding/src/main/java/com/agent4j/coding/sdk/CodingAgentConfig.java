package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProvider;
import com.agent4j.core.tool.ToolRegistry;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Provider-neutral inputs for constructing a {@link CodingAgentRuntime}. */
public final class CodingAgentConfig {
    private final String apiKey;
    private final Optional<String> baseUrl;
    private final Optional<String> provider;
    private final String model;
    private final BuiltInProviderCatalog providerCatalog;
    private final List<Path> modelsJsonFiles;
    private final List<AiModelReference> additionalModels;
    private final List<AiProvider> extensionProviders;
    private final Optional<ToolRegistry> toolRegistry;
    private final Optional<Integer> maxOutputTokens;
    private final Path workspace;
    private final Path sessionDirectory;
    private final boolean ownsWorkspace;
    private final boolean ownsSessionDirectory;
    private final Clock clock;

    private CodingAgentConfig(Builder builder) {
        this.apiKey = builder.apiKey;
        this.baseUrl = builder.baseUrl;
        this.provider = builder.provider;
        this.model = builder.model;
        this.providerCatalog = builder.providerCatalog;
        this.modelsJsonFiles = List.copyOf(builder.modelsJsonFiles);
        this.additionalModels = List.copyOf(builder.additionalModels);
        this.extensionProviders = List.copyOf(builder.extensionProviders);
        this.toolRegistry = builder.toolRegistry;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.workspace = builder.workspace;
        this.sessionDirectory = builder.sessionDirectory;
        this.ownsWorkspace = builder.ownsWorkspace;
        this.ownsSessionDirectory = builder.ownsSessionDirectory;
        this.clock = builder.clock;
    }

    public static Builder builder(String apiKey, String model, Path workspace, Path sessionDirectory) {
        return new Builder(apiKey, model, workspace, sessionDirectory);
    }

    public String apiKey() { return apiKey; }
    public Optional<String> baseUrl() { return baseUrl; }
    public Optional<String> provider() { return provider; }
    public String model() { return model; }
    public BuiltInProviderCatalog providerCatalog() { return providerCatalog; }
    public List<Path> modelsJsonFiles() { return modelsJsonFiles; }
    public List<AiModelReference> additionalModels() { return additionalModels; }
    public List<AiProvider> extensionProviders() { return extensionProviders; }
    public Optional<ToolRegistry> toolRegistry() { return toolRegistry; }
    public Optional<Integer> maxOutputTokens() { return maxOutputTokens; }
    public Path workspace() { return workspace; }
    public Path sessionDirectory() { return sessionDirectory; }
    public boolean ownsWorkspace() { return ownsWorkspace; }
    public boolean ownsSessionDirectory() { return ownsSessionDirectory; }
    public Clock clock() { return clock; }

    public static final class Builder {
        private final String apiKey;
        private final String model;
        private final Path workspace;
        private final Path sessionDirectory;
        private Optional<String> baseUrl = Optional.empty();
        private Optional<String> provider = Optional.empty();
        private BuiltInProviderCatalog providerCatalog = BuiltInProviderCatalog.defaults();
        private final List<Path> modelsJsonFiles = new ArrayList<>();
        private final List<AiModelReference> additionalModels = new ArrayList<>();
        private final List<AiProvider> extensionProviders = new ArrayList<>();
        private Optional<ToolRegistry> toolRegistry = Optional.empty();
        private Optional<Integer> maxOutputTokens = Optional.empty();
        private boolean ownsWorkspace;
        private boolean ownsSessionDirectory;
        private Clock clock = Clock.systemUTC();

        private Builder(String apiKey, String model, Path workspace, Path sessionDirectory) {
            this.apiKey = requireText(apiKey, "apiKey");
            this.model = requireText(model, "model");
            this.workspace = normalize(workspace, "workspace");
            this.sessionDirectory = normalize(sessionDirectory, "sessionDirectory");
        }

        public Builder baseUrl(String baseUrl) { this.baseUrl = Optional.of(requireText(baseUrl, "baseUrl")); return this; }
        public Builder provider(String provider) { this.provider = Optional.of(requireText(provider, "provider")); return this; }
        public Builder providerCatalog(BuiltInProviderCatalog catalog) {
            this.providerCatalog = Objects.requireNonNull(catalog, "catalog"); return this;
        }
        public Builder modelsJson(Path file) { modelsJsonFiles.add(normalize(file, "modelsJson")); return this; }
        public Builder additionalModel(AiModelReference model) {
            additionalModels.add(Objects.requireNonNull(model, "model")); return this;
        }
        public Builder extensionProvider(AiProvider provider) {
            extensionProviders.add(Objects.requireNonNull(provider, "provider")); return this;
        }
        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = Optional.of(Objects.requireNonNull(toolRegistry, "toolRegistry")); return this;
        }
        public Builder maxOutputTokens(int maxOutputTokens) {
            if (maxOutputTokens <= 0) throw new IllegalArgumentException("maxOutputTokens must be positive");
            this.maxOutputTokens = Optional.of(maxOutputTokens); return this;
        }
        public Builder ownsWorkspace(boolean ownsWorkspace) { this.ownsWorkspace = ownsWorkspace; return this; }
        public Builder ownsSessionDirectory(boolean ownsSessionDirectory) {
            this.ownsSessionDirectory = ownsSessionDirectory; return this;
        }
        public Builder clock(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); return this; }
        public CodingAgentConfig build() { return new CodingAgentConfig(this); }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
            return value.strip();
        }

        private static Path normalize(Path path, String name) {
            return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
        }
    }
}
