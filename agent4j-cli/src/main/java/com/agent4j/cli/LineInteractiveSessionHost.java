package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.PromptRequest;
import com.agent4j.coding.session.SessionManager;

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
    public int run(InteractiveSessionController controller, InteractiveTerminal terminal, List<String> initialMessages) throws IOException {
        InteractiveCommandRegistry registry = createCommandRegistry(controller, terminal);
        BufferedReader reader = terminal.input() instanceof BufferedReader buffered
                ? buffered
                : new BufferedReader(terminal.input());
        try (ExecutorService prompts = Executors.newSingleThreadExecutor()) {
            Future<?> active = null;
            for (String message : initialMessages) {
                active = submit(prompts, controller.session(), message, terminal);
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
                if (active != null && input.startsWith("/") && !"/abort".equals(input) && !input.startsWith("/follow-up ")) {
                    terminal.err().println("Error: command is unavailable while a prompt is active");
                    terminal.err().flush();
                    continue;
                }
                if (active == null || !input.startsWith("/follow-up ")) {
                    try {
                        InteractiveCommandResult command = registry.execute(input);
                        if (command.handled()) {
                            if (command.exit()) {
                                await(active);
                                return 0;
                            }
                            continue;
                        }
                    } catch (Exception error) {
                        terminal.err().println("Error: " + error.getMessage());
                        terminal.err().flush();
                        continue;
                    }
                }
                if (active != null) {
                    try {
                        if (input.startsWith("/follow-up ")) {
                            controller.session().followUp(input.substring("/follow-up ".length()));
                        } else {
                            controller.session().steer(input.startsWith("/steer ") ? input.substring("/steer ".length()) : input);
                        }
                    } catch (RuntimeException error) {
                        terminal.err().println("Error: " + error.getMessage());
                        terminal.err().flush();
                    }
                    continue;
                }
                active = submit(prompts, controller.session(), input, terminal);
            }
        }
    }

    private static InteractiveCommandRegistry createCommandRegistry(
            InteractiveSessionController controller,
            InteractiveTerminal terminal) {
        InteractiveCommandRegistry registry = new InteractiveCommandRegistry();
        registry.register("help", ignored -> {
            terminal.out().println("Commands: /help, /exit, /abort, /clear, /status, /name <name>, /compact, /new, /continue, /resume");
            terminal.out().flush();
            return InteractiveCommandResult.handledResult();
        });
        registry.register("exit", ignored -> InteractiveCommandResult.exitResult());
        registry.register("abort", ignored -> {
            if (!controller.session().abort("cancelled by interactive user")) {
                terminal.err().println("Error: no prompt is active");
                terminal.err().flush();
            }
            return InteractiveCommandResult.handledResult();
        });
        registry.register("clear", ignored -> {
            terminal.out().print("\u001B[2J\u001B[H");
            terminal.out().flush();
            return InteractiveCommandResult.handledResult();
        });
        registry.register("status", ignored -> {
            var info = controller.session().info();
            terminal.out().println("session: " + info.id());
            terminal.out().println("file: " + info.sessionFile());
            terminal.out().println("cwd: " + info.cwd());
            terminal.out().println("active entry: " + (info.activeEntryId() == null ? "(none)" : info.activeEntryId()));
            terminal.out().println("streaming: " + controller.session().isStreaming());
            terminal.out().flush();
            return InteractiveCommandResult.handledResult();
        });
        registry.register("name", name -> {
            if (name.isBlank()) throw new IllegalArgumentException("/name requires a non-empty value");
            SessionManager.open(controller.session().sessionFile()).appendSessionInfo(name);
            terminal.out().println("session name: " + name);
            terminal.out().flush();
            return InteractiveCommandResult.handledResult();
        });
        registry.register("compact", ignored -> {
            controller.session().compact("");
            terminal.out().println("compaction completed");
            terminal.out().flush();
            return InteractiveCommandResult.handledResult();
        });
        registry.register("new", ignored -> {
            controller.createNew();
            terminal.out().println("new session: " + controller.session().id());
            terminal.out().flush();
            return InteractiveCommandResult.handledResult();
        });
        registry.register("continue", ignored -> {
            controller.continueMostRecent();
            terminal.out().println("continued session: " + controller.session().id());
            terminal.out().flush();
            return InteractiveCommandResult.handledResult();
        });
        registry.register("resume", value -> {
            if (value.isBlank()) throw new IllegalArgumentException("/resume requires a session file path or ID");
            controller.resume(value);
            terminal.out().println("resumed session: " + controller.session().id());
            terminal.out().flush();
            return InteractiveCommandResult.handledResult();
        });
        return registry;
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
