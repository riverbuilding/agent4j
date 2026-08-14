package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;

import java.util.List;
import java.util.Objects;

/** Opens the resolved SDK session and hands it to the interactive terminal runner. */
public final class InteractiveModeRunner {
    private final InteractiveSessionRunner sessionRunner;

    public InteractiveModeRunner() {
        this(new LineInteractiveSessionRunner());
    }

    InteractiveModeRunner(InteractiveSessionRunner sessionRunner) {
        this.sessionRunner = Objects.requireNonNull(sessionRunner, "sessionRunner");
    }

    int run(CliRuntime runtime, CliSessionLifecycle lifecycle, InteractiveTerminal terminal, List<String> initialMessages) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(terminal, "terminal");
        initialMessages = initialMessages == null ? List.of() : List.copyOf(initialMessages);
        try {
            AgentSession session = lifecycle.open();
            try (InteractiveSessionController controller = new InteractiveSessionController(runtime, lifecycle, session, terminal);
                 InteractiveInterruptHandler ignored = InteractiveInterruptHandler.install(controller::session)) {
                return sessionRunner.run(controller, terminal, initialMessages);
            }
        } catch (Exception error) {
            terminal.err().println("Error: " + error.getMessage());
            return 1;
        } finally {
            terminal.out().flush();
            terminal.err().flush();
        }
    }
}
