package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModelClient;
import com.agent4j.coding.message.CodingAgentMessageConverter;
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
import com.agent4j.core.tool.InMemoryToolRegistry;
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

    private final AgentEventBus eventBus;
    private final AiModelClient modelClient;
    private final ToolRegistry toolRegistry;
    private final AgentMessageConverter messageConverter;
    private final Clock clock;

    public CodingAgentSessionRuntime() {
        this((AiModelClient) null);
    }

    public CodingAgentSessionRuntime(AiModelClient modelClient) {
        this(
                new AgentEventBus(),
                modelClient,
                InMemoryToolRegistry.builder().build(),
                CodingAgentMessageConverter.INSTANCE,
                Clock.systemUTC());
    }

    public CodingAgentSessionRuntime(AgentEventBus eventBus) {
        this(eventBus, null, InMemoryToolRegistry.builder().build(), CodingAgentMessageConverter.INSTANCE, Clock.systemUTC());
    }

    public CodingAgentSessionRuntime(
            AgentEventBus eventBus,
            AiModelClient modelClient,
            ToolRegistry toolRegistry,
            AgentMessageConverter messageConverter,
            Clock clock
    ) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.modelClient = modelClient;
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.messageConverter = Objects.requireNonNull(messageConverter, "messageConverter");
        this.clock = Objects.requireNonNull(clock, "clock");
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
        return new CodingAgentSession(
                this,
                sessionManager,
                new AgentConversationContext(sessionManager.activeAgentMessages(), List.of()));
    }

    @Override
    public AgentSession resumeSession(ResumeSessionRequest request) {
        throw new UnsupportedOperationException("resumeSession is not implemented yet");
    }

    @Override
    public AgentSession importSession(ImportSessionRequest request) {
        throw new UnsupportedOperationException("importSession is not implemented yet");
    }

    @Override
    public AgentSession cloneSession(CloneSessionRequest request) {
        throw new UnsupportedOperationException("cloneSession is not implemented yet");
    }

    @Override
    public AgentSession forkSession(ForkSessionRequest request) {
        throw new UnsupportedOperationException("forkSession is not implemented yet");
    }

    @Override
    public EventSubscription subscribe(Consumer<AgentEvent> subscriber) {
        return eventBus.subscribe(subscriber);
    }

    PromptResult prompt(CodingAgentSession session, PromptRequest request) throws Exception {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(request, "request");
        if (modelClient == null) {
            throw new IllegalStateException("model client is not configured");
        }
        SessionManager sessionManager = session.sessionManager();
        AgentMessage promptMessage = promptMessage(sessionManager, request);
        List<AgentMessage> messages = java.util.stream.Stream
                .concat(sessionManager.activeAgentMessages().stream(), java.util.stream.Stream.of(promptMessage))
                .toList();
        AgentLoopResult loopResult = new AgentLoop(modelClient, toolRegistry, eventBus, messageConverter)
                .runTurn(new AgentLoopRequest(
                        session.id(),
                        turnId(),
                        promptMessage.id(),
                        messages,
                        session.cwd(),
                        clock,
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
                Instant.now(clock),
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
}
