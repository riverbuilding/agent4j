package com.agent4j.coding.sdk;

import com.agent4j.ai.AiGenerationOptions;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.ai.openai.OpenAiResponsesProviderOptions;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.EventSubscription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Public entry point for configuring a coding agent and creating its sessions. */
public final class CodingAgentRuntime {
    private static final String OPENAI_PROVIDER_ID = "openai";

    private final CodingAgentSessionRuntime sessionRuntime;
    private final AiModelReference defaultModel;

    private CodingAgentRuntime(CodingAgentSessionRuntime sessionRuntime, AiModelReference defaultModel) {
        this.sessionRuntime = Objects.requireNonNull(sessionRuntime, "sessionRuntime");
        this.defaultModel = Objects.requireNonNull(defaultModel, "defaultModel");
    }

    public static CodingAgentRuntime openAi(OpenAiCodingAgentConfig config) {
        Objects.requireNonNull(config, "config");
        AiModelReference model = new AiModelReference(OPENAI_PROVIDER_ID, config.model());
        AiModel configuredModel = new AiModel(model, model.modelId());
        OpenAiResponsesProviderOptions defaults = OpenAiResponsesProviderOptions.defaults(List.of(configuredModel));
        OpenAiResponsesProviderOptions provider = new OpenAiResponsesProviderOptions(
                defaults.id(),
                defaults.name(),
                defaults.endpoint(),
                defaults.models(),
                defaults.defaultHeaders(),
                request -> withMaxOutputTokens(request, config.maxOutputTokens()),
                defaults.features());
        CodingAgentRuntimeServices services = CodingAgentRuntimeServices.builder()
                .openAi(OpenAiCodingRuntimeOptions.builder(model)
                        .models(List.of(configuredModel))
                        .credentialStore(new InMemoryAuthCredentialStore())
                        .responsesProvider(provider)
                        .build())
                .toolRegistry(config.toolRegistry())
                .build();
        services.loginService().loginApiKey(new ApiKeyLoginRequest(OPENAI_PROVIDER_ID, config.apiKey(), config.baseUrl()));
        return new CodingAgentRuntime(new CodingAgentSessionRuntime(services), model);
    }

    public AiModelReference defaultModel() {
        return defaultModel;
    }

    public CodingAgentSession createSession(Path sessionFile, Path workspace) throws Exception {
        return createSession(new CreateSessionRequest(sessionFile, workspace, Optional.empty(), Optional.of(defaultModel)));
    }

    public CodingAgentSession createSession(CreateSessionRequest request) throws Exception {
        return (CodingAgentSession) sessionRuntime.createSession(request);
    }

    public EventSubscription subscribe(Consumer<AgentEvent> subscriber) {
        return sessionRuntime.subscribe(subscriber);
    }

    private static AiProviderRequest withMaxOutputTokens(
            AiProviderRequest request,
            Optional<Integer> maxOutputTokens
    ) {
        if (maxOutputTokens.isEmpty()) {
            return request;
        }
        AiStreamOptions options = request.options();
        AiGenerationOptions generation = options.generation();
        return new AiProviderRequest(
                request.model(),
                request.turn(),
                request.context(),
                new AiStreamOptions(
                        options.signal(),
                        options.timeout(),
                        options.maxRetries(),
                        options.headers(),
                        options.attributes(),
                        new AiGenerationOptions(
                                maxOutputTokens,
                                generation.temperature(),
                                generation.topP(),
                                generation.topK(),
                                generation.toolChoice(),
                                generation.parallelToolCalls(),
                                generation.metadata())));
    }
}
