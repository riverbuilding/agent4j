package com.agent4j.coding.sdk;

import com.agent4j.coding.session.SessionHeader;
import com.agent4j.coding.session.SessionManager;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.event.EventSubscription;
import com.agent4j.core.runtime.AgentConversationContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class CodingAgentSessionRuntime implements AgentSessionRuntime {
    private final AgentEventBus eventBus;

    public CodingAgentSessionRuntime() {
        this(new AgentEventBus());
    }

    public CodingAgentSessionRuntime(AgentEventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
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
}
