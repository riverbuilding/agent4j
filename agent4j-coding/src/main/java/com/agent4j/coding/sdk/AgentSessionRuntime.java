package com.agent4j.coding.sdk;

import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.EventSubscription;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Services and lifecycle boundary for PI-style coding-agent sessions.
 */
public interface AgentSessionRuntime {
    AgentSession createSession(CreateSessionRequest request) throws Exception;

    AgentSession resumeSession(ResumeSessionRequest request) throws Exception;

    AgentSession importSession(ImportSessionRequest request) throws Exception;

    AgentSession cloneSession(CloneSessionRequest request) throws Exception;

    AgentSession forkSession(ForkSessionRequest request) throws Exception;

    EventSubscription subscribe(Consumer<AgentEvent> subscriber);

    default EventSubscription subscribeSession(String sessionId, Consumer<AgentEvent> subscriber) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(subscriber, "subscriber");
        return subscribe(event -> {
            if (sessionId.equals(event.sessionId())) {
                subscriber.accept(event);
            }
        });
    }
}
