package com.agent4j.coding.sdk;

import com.agent4j.ai.AiGenerationOptions;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelClient;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderRegistry;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.ai.openai.OpenAiResponsesProvider;
import com.agent4j.ai.openai.OpenAiResponsesProviderOptions;
import com.agent4j.coding.message.CodingAgentMessageConverter;
import com.agent4j.coding.runtime.CodingBranchSummarizer;
import com.agent4j.coding.runtime.CodingSessionCompactor;
import com.agent4j.coding.session.SessionManager;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.event.EventSubscription;
import com.agent4j.core.runtime.AgentConversationContext;
import com.agent4j.core.runtime.AgentMessageConverter;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.ToolRegistry;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Public entry point for configuring a coding agent, its authentication, and its sessions. */
public final class CodingAgentRuntime {
    private static final String OPENAI_PROVIDER_ID = "openai";

    private final AgentEventBus eventBus;
    private final AiModelClient modelClient;
    private final AiProviderRegistry providerRegistry;
    private final ToolRegistry toolRegistry;
    private final AgentMessageConverter messageConverter;
    private final Clock clock;
    private final CodingSessionCompactor sessionCompactor;
    private final CodingBranchSummarizer branchSummarizer;
    private final LoginService loginService;
    private final Optional<AiModelReference> defaultModel;

    public CodingAgentRuntime() {
        this(builder().buildState());
    }

    public CodingAgentRuntime(AiModelClient modelClient) {
        this(builder().modelClient(modelClient).buildState());
    }

    public CodingAgentRuntime(AgentEventBus eventBus) {
        this(builder().eventBus(eventBus).buildState());
    }

    public CodingAgentRuntime(
            AgentEventBus eventBus, AiModelClient modelClient, ToolRegistry toolRegistry,
            AgentMessageConverter messageConverter, Clock clock
    ) {
        this(builder().eventBus(eventBus).modelClient(modelClient).toolRegistry(toolRegistry)
                .messageConverter(messageConverter).clock(clock).buildState());
    }

    private CodingAgentRuntime(RuntimeState state) {
        this.eventBus = state.eventBus();
        this.modelClient = state.modelClient();
        this.providerRegistry = state.providerRegistry();
        this.toolRegistry = state.toolRegistry();
        this.messageConverter = state.messageConverter();
        this.clock = state.clock();
        this.sessionCompactor = state.sessionCompactor();
        this.branchSummarizer = state.branchSummarizer();
        this.loginService = state.loginService();
        this.defaultModel = state.defaultModel();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CodingAgentRuntime openAi(OpenAiCodingAgentConfig config) {
        Objects.requireNonNull(config, "config");
        AiModelReference model = new AiModelReference(OPENAI_PROVIDER_ID, config.model());
        AiModel configuredModel = new AiModel(model, model.modelId());
        OpenAiResponsesProviderOptions defaults = OpenAiResponsesProviderOptions.defaults(List.of(configuredModel));
        OpenAiResponsesProviderOptions provider = new OpenAiResponsesProviderOptions(
                defaults.id(), defaults.name(), defaults.endpoint(), defaults.models(), defaults.defaultHeaders(),
                request -> withMaxOutputTokens(request, config.maxOutputTokens()), defaults.features());
        CodingAgentRuntime runtime = builder()
                .openAi(OpenAiCodingRuntimeOptions.builder(model)
                        .models(List.of(configuredModel))
                        .credentialStore(new InMemoryAuthCredentialStore())
                        .responsesProvider(provider)
                        .build())
                .toolRegistry(config.toolRegistry())
                .build();
        runtime.loginService().loginApiKey(
                new ApiKeyLoginRequest(OPENAI_PROVIDER_ID, config.apiKey(), config.baseUrl()));
        return runtime;
    }

    public LoginService loginService() {
        return loginService;
    }

    public AiModelReference defaultModel() {
        return defaultModel.orElseThrow(() -> new IllegalStateException("the runtime has no configured default model"));
    }

    public Optional<AiProviderRegistry> optionalProviderRegistry() {
        return Optional.ofNullable(providerRegistry);
    }

    public CodingAgentSession createSession(Path sessionFile, Path workspace) throws Exception {
        return createSession(new CreateSessionRequest(sessionFile, workspace, Optional.empty(), Optional.of(defaultModel())));
    }

    public CodingAgentSession createSession(CreateSessionRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        SessionManager manager = SessionManager.create(
                request.sessionFile(), request.cwd(), request.sessionId().orElse(null));
        if (request.name().isPresent()) {
            manager.appendSessionInfo(request.name().orElseThrow());
        }
        if (request.model().isPresent()) {
            AiModelReference model = request.model().orElseThrow();
            manager.appendModelChange(model.providerId(), model.modelId());
        }
        return newSession(manager);
    }

    public CodingAgentSession resumeSession(Path file) throws Exception {
        return resumeSession(new ResumeSessionRequest(file));
    }

    public CodingAgentSession resumeSession(ResumeSessionRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        SessionManager manager = SessionManager.open(request.sessionFile());
        request.activeEntryId().ifPresent(manager::navigateTo);
        return newSession(manager);
    }

    public CodingAgentSession importSession(ImportSessionRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        return newSession(SessionManager.importFrom(request.sourceFile(), request.targetFile()));
    }

    public CodingAgentSession cloneSession(CloneSessionRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        return newSession(openSource(request.source()).cloneTo(request.targetFile()));
    }

    public CodingAgentSession forkSession(ForkSessionRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        SessionManager source = openSource(request.source());
        String active = request.activeEntryId().orElse(request.source().activeEntryId());
        if (active != null) {
            source.navigateTo(active);
        }
        return newSession(source.forkToActivePath(request.targetFile(), request.cwd().orElse(null)));
    }
    public EventSubscription subscribe(Consumer<AgentEvent> subscriber) {
        return eventBus.subscribe(subscriber);
    }

    public EventSubscription subscribeSession(String sessionId, Consumer<AgentEvent> subscriber) {
        return subscribe(event -> {
            if (sessionId.equals(event.sessionId())) {
                subscriber.accept(event);
            }
        });
    }

    AgentEventBus eventBus() {
        return eventBus;
    }

    Optional<AiModelClient> optionalModelClient() {
        return Optional.ofNullable(modelClient);
    }

    ToolRegistry toolRegistry() {
        return toolRegistry;
    }

    AgentMessageConverter messageConverter() {
        return messageConverter;
    }

    Clock clock() {
        return clock;
    }

    CodingSessionCompactor sessionCompactor() {
        return sessionCompactor;
    }

    CodingBranchSummarizer branchSummarizer() {
        return branchSummarizer;
    }

    private CodingAgentSession newSession(SessionManager manager) {
        return new CodingAgentSession(this, manager, new AgentConversationContext(manager.activeAgentMessages(), List.of()));
    }

    private static SessionManager openSource(AgentSession source) throws java.io.IOException {
        Objects.requireNonNull(source, "source");
        return SessionManager.open(source.sessionFile());
    }

    public static final class Builder {
        private AgentEventBus eventBus;
        private AiModelClient modelClient;
        private AiProviderRegistry providerRegistry;
        private ToolRegistry toolRegistry;
        private AgentMessageConverter messageConverter;
        private Clock clock;
        private CodingSessionCompactor sessionCompactor;
        private CodingBranchSummarizer branchSummarizer;
        private LoginService loginService;
        private Optional<AiModelReference> defaultModel = Optional.empty();

        public Builder eventBus(AgentEventBus eventBus) {
            this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
            return this;
        }

        public Builder modelClient(AiModelClient modelClient) {
            this.modelClient = modelClient;
            return this;
        }

        public Builder providerRegistry(AiProviderRegistry providerRegistry) {
            this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
            return this;
        }

        public Builder messageConverter(AgentMessageConverter messageConverter) {
            this.messageConverter = Objects.requireNonNull(messageConverter, "messageConverter");
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public Builder sessionCompactor(CodingSessionCompactor sessionCompactor) {
            this.sessionCompactor = Objects.requireNonNull(sessionCompactor, "sessionCompactor");
            return this;
        }

        public Builder branchSummarizer(CodingBranchSummarizer branchSummarizer) {
            this.branchSummarizer = Objects.requireNonNull(branchSummarizer, "branchSummarizer");
            return this;
        }

        public Builder loginService(LoginService loginService) {
            this.loginService = Objects.requireNonNull(loginService, "loginService");
            return this;
        }

        public Builder openAi(OpenAiCodingRuntimeOptions options) {
            Objects.requireNonNull(options, "options");
            providerRegistry(AiProviderRegistry.builder()
                    .add(new OpenAiResponsesProvider(options.responsesProvider(), options.responsesTransport()))
                    .defaultModel(options.defaultModel())
                    .build());
            LoginService resolvedLoginService = options.subscriptionLogin()
                    .<LoginService>map(subscriptionOptions -> new DefaultLoginService(
                            options.credentialStore(),
                            options.clock(),
                            new OpenAiSubscriptionLoginClient(
                                    subscriptionOptions,
                                    options.subscriptionLoginTransport())))
                    .orElseGet(() -> new DefaultLoginService(options.credentialStore(), options.clock()));
            loginService(resolvedLoginService);
            clock(options.clock());
            defaultModel = Optional.of(options.defaultModel());
            return this;
        }

        public CodingAgentRuntime build() {
            return new CodingAgentRuntime(buildState());
        }

        private RuntimeState buildState() {
            AgentEventBus resolvedEventBus = eventBus == null ? new AgentEventBus() : eventBus;
            Clock resolvedClock = clock == null ? Clock.systemUTC() : clock;
            LoginService resolvedLoginService = loginService == null
                    ? new DefaultLoginService(PersistentAuthCredentialStore.userDefault(), resolvedClock)
                    : loginService;
            return new RuntimeState(resolvedEventBus, modelClient, providerRegistry,
                    toolRegistry == null ? InMemoryToolRegistry.builder().build() : toolRegistry,
                    messageConverter == null ? CodingAgentMessageConverter.INSTANCE : messageConverter,
                    resolvedClock,
                    sessionCompactor == null ? new CodingSessionCompactor(resolvedEventBus) : sessionCompactor,
                    branchSummarizer == null ? new CodingBranchSummarizer() : branchSummarizer,
                    resolvedLoginService,
                    defaultModel);
        }
    }

    private record RuntimeState(
            AgentEventBus eventBus,
            AiModelClient modelClient,
            AiProviderRegistry providerRegistry,
            ToolRegistry toolRegistry,
            AgentMessageConverter messageConverter,
            Clock clock,
            CodingSessionCompactor sessionCompactor,
            CodingBranchSummarizer branchSummarizer,
            LoginService loginService,
            Optional<AiModelReference> defaultModel
    ) {
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
        return new AiProviderRequest(request.model(), request.turn(), request.context(), new AiStreamOptions(
                options.signal(), options.timeout(), options.maxRetries(), options.headers(), options.attributes(),
                new AiGenerationOptions(maxOutputTokens, generation.temperature(), generation.topP(), generation.topK(),
                        generation.toolChoice(), generation.parallelToolCalls(), generation.metadata())));
    }
}
