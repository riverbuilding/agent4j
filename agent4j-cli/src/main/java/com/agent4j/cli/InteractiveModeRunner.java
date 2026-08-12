package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;

import java.util.Objects;

/** Opens the resolved SDK session and hands it to the interactive terminal host. */
public final class InteractiveModeRunner {
    private final InteractiveSessionHost sessionHost;

    public InteractiveModeRunner() {
        this((session, terminal) -> {
            terminal.out().println("Interactive session ready: " + session.id());
            terminal.out().flush();
            return 0;
        });
    }

    InteractiveModeRunner(InteractiveSessionHost sessionHost) {
        this.sessionHost = Objects.requireNonNull(sessionHost, "sessionHost");
    }

    int run(CliRuntime runtime, CliSessionLifecycle lifecycle, InteractiveTerminal terminal) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(terminal, "terminal");
        try {
            AgentSession session = lifecycle.open();
            return sessionHost.run(session, terminal);
        } catch (Exception error) {
            terminal.err().println("Error: " + error.getMessage());
            return 1;
        } finally {
            terminal.out().flush();
            terminal.err().flush();
        }
    }
}
