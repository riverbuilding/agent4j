package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.PromptRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Minimal persistent line REPL over one SDK session. */
final class LineInteractiveSessionHost implements InteractiveSessionHost {
    private static final int DEFAULT_MAX_TOOL_ROUNDS = 20;
    private static final String PROMPT = "agent4j> ";

    @Override
    public int run(AgentSession session, InteractiveTerminal terminal, List<String> initialMessages) throws IOException {
        BufferedReader reader = terminal.input() instanceof BufferedReader buffered
                ? buffered
                : new BufferedReader(terminal.input());
        try (ExecutorService prompts = Executors.newSingleThreadExecutor()) {
            Future<?> active = null;
            for (String message : initialMessages) {
                active = submit(prompts, session, message, terminal);
                await(active);
            }
            while (true) {
                if (active != null && active.isDone()) {
                    await(active);
                    active = null;
                }
                terminal.out().print(PROMPT);
                terminal.out().flush();
                String line = reader.readLine();
                if (line == null) {
                    await(active);
                    return 0;
                }
                String input = line.strip();
                if (input.isEmpty()) {
                    continue;
                }
                if ("/abort".equals(input)) {
                    if (!session.abort("cancelled by interactive user")) {
                        terminal.err().println("Error: no prompt is active");
                        terminal.err().flush();
                    }
                    continue;
                }
                if (active != null) {
                    try {
                        if (input.startsWith("/follow-up ")) {
                            session.followUp(input.substring("/follow-up ".length()));
                        } else {
                            session.steer(input.startsWith("/steer ") ? input.substring("/steer ".length()) : input);
                        }
                    } catch (RuntimeException error) {
                        terminal.err().println("Error: " + error.getMessage());
                        terminal.err().flush();
                    }
                    continue;
                }
                active = submit(prompts, session, input, terminal);
            }
        }
    }

    private Future<?> submit(ExecutorService prompts, AgentSession session, String input, InteractiveTerminal terminal) {
        String prompt = input == null ? "" : input.strip();
        if (prompt.isEmpty()) {
            return null;
        }
        return prompts.submit(() -> {
            try {
                session.prompt(new PromptRequest(
                    prompt,
                    Optional.empty(),
                    DEFAULT_MAX_TOOL_ROUNDS,
                    0,
                    Optional.empty(),
                    null,
                    java.util.Map.of(),
                    List.of(),
                    List.of(),
                    null,
                    null,
                    Optional.empty()));
            } catch (Exception error) {
                terminal.err().println("Error: " + error.getMessage());
                terminal.err().flush();
            }
        });
    }

    private static void await(Future<?> active) {
        if (active == null) return;
        try {
            active.get();
        } catch (Exception ignored) {
            // Prompt task converts failures to terminal diagnostics.
        }
    }

}
