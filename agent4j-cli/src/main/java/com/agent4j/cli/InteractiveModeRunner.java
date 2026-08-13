package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.core.event.EventSubscription;

import java.util.List;
import java.util.Objects;

/** Opens the resolved SDK session and hands it to the interactive terminal host. */
public final class InteractiveModeRunner {
    private final InteractiveSessionHost sessionHost;

    public InteractiveModeRunner() {
        this(new LineInteractiveSessionHost());
    }

    InteractiveModeRunner(InteractiveSessionHost sessionHost) {
        this.sessionHost = Objects.requireNonNull(sessionHost, "sessionHost");
    }

    int run(CliRuntime runtime, CliSessionLifecycle lifecycle, InteractiveTerminal terminal, List<String> initialMessages) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(terminal, "terminal");
        initialMessages = initialMessages == null ? List.of() : List.copyOf(initialMessages);
        EventSubscription subscription = null;
        try {
            AgentSession session = lifecycle.open();
            InteractiveEventRenderer renderer = new InteractiveEventRenderer(terminal);
            subscription = runtime.sessionRuntime().subscribeSession(session.id(), renderer::render);
            try (InteractiveInterruptHandler ignored = InteractiveInterruptHandler.install(session)) {
                return sessionHost.run(session, terminal, initialMessages);
            }
        } catch (Exception error) {
            terminal.err().println("Error: " + error.getMessage());
            return 1;
        } finally {
            if (subscription != null) {
                subscription.close();
            }
            terminal.out().flush();
            terminal.err().flush();
        }
    }
}
