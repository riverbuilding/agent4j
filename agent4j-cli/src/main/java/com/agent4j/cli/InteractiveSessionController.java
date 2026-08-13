package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.core.event.EventSubscription;

import java.util.Objects;

/** Owns the selected interactive session and rebinds event rendering on session changes. */
final class InteractiveSessionController implements AutoCloseable {
    private final CliRuntime runtime;
    private final CliSessionLifecycle lifecycle;
    private final InteractiveEventRenderer renderer;
    private AgentSession session;
    private EventSubscription subscription;

    InteractiveSessionController(CliRuntime runtime, CliSessionLifecycle lifecycle, AgentSession session, InteractiveTerminal terminal) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.renderer = new InteractiveEventRenderer(Objects.requireNonNull(terminal, "terminal"));
        replace(Objects.requireNonNull(session, "session"));
    }

    AgentSession session() { return session; }

    void createNew() throws Exception { replace(lifecycle.createNew()); }

    void continueMostRecent() throws Exception { replace(lifecycle.continueMostRecent()); }

    void resume(String value) throws Exception { replace(lifecycle.resume(value)); }

    private void replace(AgentSession next) {
        if (subscription != null) {
            subscription.close();
        }
        session = next;
        subscription = runtime.sessionRuntime().subscribeSession(next.id(), renderer::render);
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
        }
    }
}
