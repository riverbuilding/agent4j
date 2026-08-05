package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModelClient;
import com.agent4j.coding.session.SessionEntry;
import com.agent4j.coding.session.SessionHeader;
import com.agent4j.coding.session.SessionManager;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.event.EventSubscription;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.runtime.AbortController;
import com.agent4j.core.runtime.AgentConversationContext;
import com.agent4j.core.runtime.AgentLoop;
import com.agent4j.core.runtime.AgentLoopRequest;
import com.agent4j.core.runtime.AgentLoopResult;
import com.agent4j.core.runtime.AgentMessageConverter;
import com.agent4j.core.tool.ToolRegistry;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class CodingAgentSessionRuntime implements AgentSessionRuntime {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final CodingAgentRuntimeServices services;

    public CodingAgentSessionRuntime() {
        this(CodingAgentRuntimeServices.defaults());
    }

    public CodingAgentSessionRuntime(AiModelClient modelClient) {
        this(CodingAgentRuntimeServices.withModelClient(modelClient));
    }

    public CodingAgentSessionRuntime(AgentEventBus eventBus) {
        this(CodingAgentRuntimeServices.builder().eventBus(eventBus).build());
    }

    public CodingAgentSessionRuntime(
            AgentEventBus eventBus,
            AiModelClient modelClient,
            ToolRegistry toolRegistry,
            AgentMessageConverter messageConverter,
            Clock clock
    ) {
        this(CodingAgentRuntimeServices.builder()
                .eventBus(eventBus)
                .modelClient(modelClient)
                .toolRegistry(toolRegistry)
                .messageConverter(messageConverter)
                .clock(clock)
                .build());
    }

    public CodingAgentSessionRuntime(CodingAgentRuntimeServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    @Override
    public AgentSession createSession(CreateSessionRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        SessionManager sessionManager = SessionManager.create(request.sessionFile(), request.cwd());
        if (request.name().isPresent()) {
            sessionManager.appendSessionInfo(request.name().orElseThrow());
        }
        if (request.model().isPresent()) {
            var model = request.model().orElseThrow();
            sessionManager.appendModelChange(model.providerId(), model.modelId());
        }
        return newSession(sessionManager);
    }

    @Override
    public AgentSession resumeSession(ResumeSessionRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        SessionManager sessionManager = SessionManager.open(request.sessionFile());
        request.activeEntryId().ifPresent(sessionManager::navigateTo);
        return newSession(sessionManager);
    }

    @Override
    public AgentSession importSession(ImportSessionRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        return newSession(SessionManager.importFrom(request.sourceFile(), request.targetFile()));
    }

    @Override
    public AgentSession cloneSession(CloneSessionRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        SessionManager source = openSource(request.source());
        return newSession(source.cloneTo(request.targetFile()));
    }

    @Override
    public AgentSession forkSession(ForkSessionRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        SessionManager source = openSource(request.source());
        String activeEntryId = request.activeEntryId().orElse(request.source().activeEntryId());
        if (activeEntryId != null) {
            source.navigateTo(activeEntryId);
        }
        return newSession(source.forkToActivePath(request.targetFile()));
    }

    @Override
    public EventSubscription subscribe(Consumer<AgentEvent> subscriber) {
        return services.eventBus().subscribe(subscriber);
    }

    PromptResult prompt(CodingAgentSession session, PromptRequest request) throws Exception {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(request, "request");
        AiModelClient modelClient = services.optionalModelClient()
                .orElseThrow(() -> new IllegalStateException("model client is not configured"));
        ToolRegistry toolRegistry = services.toolRegistry();
        AgentMessageConverter messageConverter = services.messageConverter();
        SessionManager sessionManager = session.sessionManager();
        AgentMessage promptMessage = promptMessage(sessionManager, request);
        List<AgentMessage> messages = java.util.stream.Stream
                .concat(sessionManager.activeAgentMessages().stream(), java.util.stream.Stream.of(promptMessage))
                .toList();
        AgentLoopResult loopResult = new AgentLoop(modelClient, toolRegistry, services.eventBus(), messageConverter)
                .runTurn(new AgentLoopRequest(
                        session.id(),
                        turnId(),
                        promptMessage.id(),
                        messages,
                        session.cwd(),
                        services.clock(),
                        request.abortSignal().orElseGet(() -> new AbortController().signal()),
                        request.toolAttributes(),
                        null,
                        request.maxToolRounds(),
                        request.maxModelRetries(),
                        request.modelTimeout(),
                        request.toolExecutionMode(),
                        List.of(promptMessage),
                        request.steeringMessages(),
                        request.followUpMessages(),
                        request.steeringMode(),
                        request.followUpMode()));
        List<SessionEntry> persisted = sessionManager.appendAgentLoopResult(loopResult);
        return new PromptResult(session, loopResult, persisted);
    }

    AgentSessionInfo sessionInfo(SessionManager sessionManager) {
        Objects.requireNonNull(sessionManager, "sessionManager");
        SessionHeader header = sessionManager.document().header().header()
                .orElseThrow(() -> new IllegalStateException("session header is missing"));
        return new AgentSessionInfo(
                header.id(),
                sessionManager.sessionFile(),
                cwd(header),
                sessionManager.activeEntryId());
    }

    private static Path cwd(SessionHeader header) {
        if (header.cwd() == null || header.cwd().isBlank()) {
            throw new IllegalStateException("session header cwd is missing");
        }
        return Path.of(header.cwd());
    }

    private AgentMessage promptMessage(SessionManager sessionManager, PromptRequest request) {
        return new AgentMessage(
                messageId(),
                sessionManager.activeEntryId(),
                Instant.now(services.clock()),
                AgentMessageRole.USER,
                JSON.textNode(request.prompt()),
                JSON.objectNode());
    }

    private static String messageId() {
        return "msg-" + UUID.randomUUID();
    }

    private static String turnId() {
        return "turn-" + UUID.randomUUID();
    }

    private CodingAgentSession newSession(SessionManager sessionManager) {
        return new CodingAgentSession(
                this,
                sessionManager,
                new AgentConversationContext(sessionManager.activeAgentMessages(), List.of()));
    }

    private static SessionManager openSource(AgentSession source) throws java.io.IOException {
        Objects.requireNonNull(source, "source");
        return SessionManager.open(source.sessionFile());
    }
}
