package com.agent4j.cli;

import com.agent4j.coding.session.SessionManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Minimal persistent line REPL over one SDK session. */
final class LineInteractiveSessionRunner implements InteractiveSessionRunner {
    private static final String PROMPT = "agent4j> ";

    @Override
    public int run(InteractiveSessionController controller, InteractiveTerminal terminal, List<String> initialMessages) throws IOException {
        BufferedReader reader = terminal.input() instanceof BufferedReader buffered
                ? buffered
                : new BufferedReader(terminal.input());
        InteractiveCommandRegistry registry = createCommandRegistry(controller, terminal, reader);
        try (ExecutorService prompts = Executors.newSingleThreadExecutor()) {
            Future<?> active = null;
            for (String message : initialMessages) {
                active = submit(prompts, controller, message, terminal);
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
                if (active != null && active.isDone()) {
                    await(active);
                    active = null;
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
                if (active != null && !controller.session().isStreaming() && !input.startsWith("/follow-up ")) {
                    await(active);
                    active = null;
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
                active = submit(prompts, controller, input, terminal);
                if (active != null && !controller.session().isStreaming()) {
                    await(active);
                }
            }
        }
    }

    private static InteractiveCommandRegistry createCommandRegistry(
            InteractiveSessionController controller,
            InteractiveTerminal terminal,
            BufferedReader reader) {
        InteractiveCommandRegistry registry = new InteractiveCommandRegistry();
        registry.register("help", ignored -> {
            terminal.out().println("Commands: /help, /exit, /abort, /clear, /status, /model [provider/]model, /name <name>, /compact, /new, /continue, /resume [path|id]");
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
            terminal.out().println("model: " + controller.model().displayName());
            terminal.out().flush();
            return InteractiveCommandResult.handledResult();
        });
        registry.register("model", value -> {
            if (value.isBlank()) {
                terminal.out().println("model: " + controller.model().displayName());
            } else {
                controller.selectModel(value);
                terminal.out().println("model: " + controller.model().displayName());
            }
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
            String selection = value.isBlank() ? selectSession(controller, terminal, reader) : value;
            if (controller.lifecycle().isCrossProject(selection)) {
                terminal.out().print("Session belongs to another project. Fork it into this project? [y/N] ");
                terminal.out().flush();
                String confirmation = reader.readLine();
                if (confirmation == null || !confirmation.strip().equalsIgnoreCase("y")) {
                    throw new IllegalArgumentException("cross-project resume cancelled");
                }
            }
            controller.resume(selection, true);
            terminal.out().println("resumed session: " + controller.session().id());
            terminal.out().flush();
            return InteractiveCommandResult.handledResult();
        });
        return registry;
    }

    private Future<?> submit(ExecutorService prompts, InteractiveSessionController controller, String input, InteractiveTerminal terminal) {
        String prompt = input == null ? "" : input.strip();
        if (prompt.isEmpty()) {
            return null;
        }
        return prompts.submit(() -> {
            try {
                controller.session().prompt(CliPromptRequestFactory.create(prompt, controller.promptModel(), Optional.empty()));
            } catch (Exception error) {
                terminal.err().println("Error: " + error.getMessage());
                terminal.err().flush();
            }
        });
    }

    private static String selectSession(InteractiveSessionController controller, InteractiveTerminal terminal, BufferedReader reader) throws IOException {
        List<CliSessionLifecycle.SessionCandidate> candidates = controller.lifecycle().candidates();
        if (candidates.isEmpty()) throw new IllegalArgumentException("no sessions found");
        terminal.out().println("Sessions:");
        for (int index = 0; index < candidates.size(); index++) {
            var candidate = candidates.get(index);
            terminal.out().println((index + 1) + ") " + candidate.id() + " " + candidate.cwd());
        }
        terminal.out().print("Select session: ");
        terminal.out().flush();
        String selection = reader.readLine();
        if (selection == null || selection.isBlank()) throw new IllegalArgumentException("session selection cancelled");
        try {
            int index = Integer.parseInt(selection.strip()) - 1;
            if (index < 0 || index >= candidates.size()) throw new IllegalArgumentException("invalid session selection");
            return candidates.get(index).path().toString();
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid session selection: " + selection);
        }
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
