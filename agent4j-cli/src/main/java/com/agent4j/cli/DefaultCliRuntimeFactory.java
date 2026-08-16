package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.sdk.ApiKeyLoginRequest;
import com.agent4j.coding.sdk.AuthCredentialStore;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.InMemoryAuthCredentialStore;
import com.agent4j.coding.sdk.DefaultLoginService;
import com.agent4j.coding.sdk.LoginService;
import com.agent4j.coding.sdk.ModelRuntime;
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
        boolean runtimeApiKey = request.apiKey().isPresent();
        AuthCredentialStore runtimeCredentialStore = runtimeApiKey ? new InMemoryAuthCredentialStore() : credentialStore;
        LoginService loginService = new DefaultLoginService(runtimeCredentialStore, clock);
        Optional<String> requestedProvider = request.provider().or(() -> discovery.settings().textField("defaultProvider"));
        Optional<String> requestedModel = request.model().or(() -> discovery.settings().textField("defaultModel"));
        if (request.apiKey().isPresent() && requestedProvider.isEmpty() && requestedModel.isEmpty()) {
            throw new IllegalArgumentException("--api-key requires --model or --provider");
        }
        if (request.apiKey().isPresent() && requestedProvider.isPresent()) {
            loginService.loginApiKey(new ApiKeyLoginRequest(requestedProvider.orElseThrow(), request.apiKey().orElseThrow()));
        }
        ModelRuntime modelRuntime = ModelRuntime.builder(loginService)
                .modelsJson(discovery.directories().globalAgentDir().resolve("models.json"))
                .modelsJson(discovery.directories().projectAgentDir().resolve("models.json"))
                .build();
        AiModelReference model = modelRuntime.resolve(requestedProvider, requestedModel);
        if (request.apiKey().isPresent() && requestedProvider.isEmpty()) {
            loginService.loginApiKey(new ApiKeyLoginRequest(model.providerId(), request.apiKey().orElseThrow()));
        }
        CodingAgentRuntime runtime = CodingAgentRuntime.builder()
                .providerRegistry(modelRuntime.registry(model))
                .loginService(loginService)
                .clock(clock)
                .toolRegistry(CliToolSelector.select(toolRegistry, request.toolSelection()))
                .build();

        return new CliRuntime(runtime, discovery, model, runtime.optionalProviderRegistry());
    }

}
