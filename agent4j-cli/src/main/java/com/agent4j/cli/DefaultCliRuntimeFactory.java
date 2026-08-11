package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.resource.AgentSettings;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.sdk.AgentSessionRuntime;
import com.agent4j.coding.sdk.ApiKeyLoginRequest;
import com.agent4j.coding.sdk.AuthCredentialStore;
import com.agent4j.coding.sdk.CodingAgentRuntimeServices;
import com.agent4j.coding.sdk.CodingAgentSessionRuntime;
import com.agent4j.coding.sdk.InMemoryAuthCredentialStore;
import com.agent4j.coding.sdk.OpenAiCodingRuntimeOptions;
import com.agent4j.coding.sdk.PersistentAuthCredentialStore;
import com.agent4j.coding.tool.CodingTools;
import com.agent4j.core.tool.ToolRegistry;

import java.time.Clock;
import java.util.Optional;
import java.util.Objects;

/**
 * Builds the Phase 9 session runtime for CLI modes without duplicating agent
 * loop, tool, provider, or credential behavior in the CLI module.
 */
public final class DefaultCliRuntimeFactory implements CliRuntimeFactory {
    private static final String OPENAI_PROVIDER = "openai";

    private final ResourceLoader resourceLoader;
    private final AuthCredentialStore credentialStore;
    private final ToolRegistry toolRegistry;
    private final Clock clock;

    public DefaultCliRuntimeFactory() {
        this(
                new ResourceLoader(),
                PersistentAuthCredentialStore.userDefault(),
                CodingTools.localDefaults().registry(),
                Clock.systemUTC());
    }

    public DefaultCliRuntimeFactory(
            ResourceLoader resourceLoader,
            AuthCredentialStore credentialStore,
            ToolRegistry toolRegistry,
            Clock clock
    ) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CliRuntime create(CliRuntimeRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        ResourceDiscovery discovery = resourceLoader.discover(
                ResourceDiscoveryOptions.enabled(request.homeDirectory(), request.cwd()));
        AiModelReference model = resolveModel(request, discovery.settings());
        if (!OPENAI_PROVIDER.equals(model.providerId())) {
            throw new IllegalArgumentException(
                    "provider is not configured by the current CLI bootstrap: " + model.providerId());
        }

        boolean runtimeApiKey = request.apiKey().isPresent();
        AuthCredentialStore runtimeCredentialStore = runtimeApiKey ? new InMemoryAuthCredentialStore() : credentialStore;
        CodingAgentRuntimeServices services = CodingAgentRuntimeServices.builder()
                .openAi(OpenAiCodingRuntimeOptions.builder(model)
                        .credentialStore(runtimeCredentialStore)
                        .clock(clock)
                        .build())
                .toolRegistry(toolRegistry)
                .build();
        request.apiKey().ifPresent(apiKey -> services.loginService().loginApiKey(
                new ApiKeyLoginRequest(model.providerId(), apiKey)));

        AgentSessionRuntime runtime = new CodingAgentSessionRuntime(services);
        return new CliRuntime(runtime, discovery, model);
    }

    private static AiModelReference resolveModel(CliRuntimeRequest request, AgentSettings settings) {
        Optional<String> configuredProvider = request.provider().or(() -> settings.textField("defaultProvider"));
        Optional<String> configuredModel = request.model().or(() -> settings.textField("defaultModel"));
        if (configuredModel.isPresent() && configuredModel.orElseThrow().contains("/")) {
            String[] parts = configuredModel.orElseThrow().split("/", 2);
            if (parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException("model must use provider/model form");
            }
            if (configuredProvider.isPresent() && !configuredProvider.orElseThrow().equals(parts[0])) {
                throw new IllegalArgumentException("--provider conflicts with the provider encoded in --model");
            }
            return new AiModelReference(parts[0], parts[1]);
        }
        String provider = configuredProvider.orElseThrow(() -> new IllegalArgumentException(
                "provider is required; pass --provider or configure defaultProvider"));
        String model = configuredModel.orElseGet(() -> providerDefaultModel(settings, provider)
                .orElseThrow(() -> new IllegalArgumentException(
                        "model is required; pass --model or configure defaultModel")));
        return new AiModelReference(provider, model);
    }

    private static Optional<String> providerDefaultModel(AgentSettings settings, String provider) {
        return Optional.ofNullable(settings.values().at("/models/" + provider + "/default"))
                .filter(com.fasterxml.jackson.databind.JsonNode::isTextual)
                .map(com.fasterxml.jackson.databind.JsonNode::asText)
                .map(String::strip)
                .filter(value -> !value.isEmpty());
    }
}
