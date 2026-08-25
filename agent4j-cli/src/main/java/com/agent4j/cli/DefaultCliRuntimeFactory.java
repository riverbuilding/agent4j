package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.EnvironmentAiAuthStore;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.resource.SystemPromptBuilder;
import com.agent4j.coding.sdk.ApiKeyLoginRequest;
import com.agent4j.coding.sdk.AuthCredentialStore;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.InMemoryAuthCredentialStore;
import com.agent4j.coding.sdk.DefaultLoginService;
import com.agent4j.coding.sdk.LoginService;
import com.agent4j.coding.sdk.ModelRuntime;
import com.agent4j.coding.sdk.BuiltInProviderCatalog;
import com.agent4j.coding.sdk.OpenAiCompatibleProviderConfig;
import com.agent4j.coding.sdk.OpenAiCompatibleProviderConfigLoader;
import com.agent4j.coding.sdk.PersistentAuthCredentialStore;
import com.agent4j.coding.sdk.RuntimePromptResolver;
import com.agent4j.coding.tool.CodingTools;
import com.agent4j.core.tool.ToolRegistry;

import java.time.Clock;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        Map<String, String> environment = System.getenv();
        ResourceDiscovery discovery = resourceLoader.discover(
                ResourceDiscoveryOptions.enabled(request.homeDirectory(), request.cwd()));
        boolean runtimeApiKey = request.apiKey().isPresent();
        AuthCredentialStore runtimeCredentialStore = runtimeApiKey ? new InMemoryAuthCredentialStore() : credentialStore;
        List<Path> modelFiles = List.of(
                discovery.directories().globalAgentDir().resolve("models.json"),
                discovery.directories().projectAgentDir().resolve("models.json"));
        List<OpenAiCompatibleProviderConfig> compatibleProviders = OpenAiCompatibleProviderConfigLoader.load(modelFiles);
        Map<String, EnvironmentAiAuthStore.ProviderEnvironmentAuth> credentials = new LinkedHashMap<>(
                BuiltInProviderCatalog.defaults().credentialDescriptors());
        compatibleProviders.forEach(provider -> credentials.put(provider.id(), provider.credentials()));
        LoginService loginService = new DefaultLoginService(runtimeCredentialStore, clock,
                new EnvironmentAiAuthStore(environment, credentials));
        Optional<String> requestedProvider = request.provider().or(() -> discovery.settings().textField("defaultProvider"));
        Optional<String> requestedModel = requestedModel(
                request.model(), discovery.settings().textField("defaultModel"), environment);
        if (request.apiKey().isPresent() && requestedProvider.isEmpty() && requestedModel.isEmpty()) {
            throw new IllegalArgumentException("--api-key requires --model or --provider");
        }
        if (request.apiKey().isPresent() && requestedProvider.isPresent()) {
            loginService.loginApiKey(new ApiKeyLoginRequest(
                    requestedProvider.orElseThrow(), request.apiKey().orElseThrow(), request.baseUrl()));
        }
        ModelRuntime modelRuntime = ModelRuntime.builder(loginService)
                .openAiCompatibleProviders(compatibleProviders)
                .modelsJson(modelFiles.get(0))
                .modelsJson(modelFiles.get(1))
                .build();
        AiModelReference model = modelRuntime.resolve(requestedProvider, requestedModel);
        if (request.apiKey().isPresent() && requestedProvider.isEmpty()) {
            loginService.loginApiKey(new ApiKeyLoginRequest(model.providerId(), request.apiKey().orElseThrow(), request.baseUrl()));
        }
        ToolRegistry selectedTools = CliToolSelector.select(toolRegistry, request.toolSelection());
        String systemPrompt = new SystemPromptBuilder().build(
                discovery,
                selectedTools.specs(),
                request.systemPrompt(),
                request.appendSystemPrompts());
        CodingAgentRuntime runtime = CodingAgentRuntime.builder()
                .providerRegistry(modelRuntime.registry(model))
                .loginService(loginService)
                .clock(clock)
                .toolRegistry(selectedTools)
                .promptResolver(new RuntimePromptResolver(
                        resourceLoader,
                        new SystemPromptBuilder(),
                        ResourceDiscoveryOptions.enabled(request.homeDirectory(), request.cwd()),
                        request.systemPrompt(),
                        request.appendSystemPrompts()))
                .build();

        return new CliRuntime(runtime, discovery, model, runtime.optionalProviderRegistry(), systemPrompt);
    }

    private static Optional<String> environmentValue(Map<String, String> environment, String name) {
        return Optional.ofNullable(environment.get(name)).map(String::strip).filter(value -> !value.isEmpty());
    }

    static Optional<String> requestedModel(
            Optional<String> commandLineModel,
            Optional<String> configuredModel,
            Map<String, String> environment
    ) {
        return commandLineModel.or(() -> configuredModel).or(() -> environmentValue(environment, "AGENT4J_MODEL"));
    }

}
