package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.session.SessionManager;
import com.agent4j.core.event.EventSubscription;

import java.util.Objects;
import java.io.IOException;

/** Owns the selected interactive session and rebinds event rendering on session changes. */
final class InteractiveSessionController implements AutoCloseable {
    private final CliRuntime runtime;
    private final CliSessionLifecycle lifecycle;
    private final TerminalEventRenderer renderer;
    private AgentSession session;
    private EventSubscription subscription;
    private AiModelReference model;

    InteractiveSessionController(CliRuntime runtime, CliSessionLifecycle lifecycle, AgentSession session, InteractiveTerminal terminal) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.renderer = TerminalEventRenderer.Factory.create(Objects.requireNonNull(terminal, "terminal"));
        replace(Objects.requireNonNull(session, "session"));
    }

    AgentSession session() { return session; }

    CliSessionLifecycle lifecycle() { return lifecycle; }

    void createNew() throws Exception { replace(lifecycle.createNew()); }

    void continueMostRecent() throws Exception { replace(lifecycle.continueMostRecent()); }

    void resume(String value) throws Exception { replace(lifecycle.resume(value)); }

    void resume(String value, boolean confirmCrossProject) throws Exception {
        replace(lifecycle.resume(value, confirmCrossProject));
    }

    AiModelReference model() { return model; }

    java.util.Optional<AiModelReference> promptModel() { return runtime.promptModel(); }

    void selectModel(String value) throws IOException {
        String[] parts = value.split("/", 2);
        AiModelReference next = parts.length == 2
                ? new AiModelReference(parts[0], parts[1])
                : new AiModelReference(model.providerId(), value);
        runtime.providerRegistry().ifPresent(registry -> registry.require(next));
        SessionManager.open(session.sessionFile()).appendModelChange(next.providerId(), next.modelId());
        model = next;
    }

    private void replace(AgentSession next) {
        if (subscription != null) {
            subscription.close();
        }
        session = next;
        try {
            model = SessionManager.open(next.sessionFile()).document().entries().stream()
                    .flatMap(entry -> entry.modelChange().stream())
                    .reduce((first, last) -> last)
                    .map(change -> new AiModelReference(change.provider(), change.modelId()))
                    .orElse(runtime.defaultModel());
        } catch (IOException error) {
            throw new IllegalStateException("cannot read session model selection", error);
        }
        subscription = runtime.runtime().subscribeSession(next.id(), renderer::render);
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
        }
    }
}
