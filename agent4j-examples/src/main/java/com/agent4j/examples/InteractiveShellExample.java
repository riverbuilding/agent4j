package com.agent4j.examples;

import com.agent4j.cli.Agent4jCli;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** 11-interactive-shell: starts the real CLI shell with streaming and one read-only tool. */
public final class InteractiveShellExample {
    private InteractiveShellExample() {
    }

    public static void main(String[] args) throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open();
        try {
            Files.createDirectories(configuration.sessionDirectory());
            printInstructions(configuration);
            int exitCode = Agent4jCli.execute(command(configuration,
                    "Call the read tool exactly once with path pom.xml. Then summarize the project's Maven modules "
                            + "in no more than two sentences."));
            if (exitCode != 0) {
                throw new IllegalStateException("interactive shell exited with code " + exitCode);
            }
        } finally {
            configuration.cleanupTemporaryDirectories();
        }
    }

    private static void printInstructions(LiveExampleConfiguration configuration) {
        System.out.println("Interactive shell walkthrough");
        System.out.println("Model: " + configuration.model());
        System.out.println("Session directory: " + configuration.sessionDirectory());
        System.out.println("Only the read tool is enabled; it cannot modify files or execute commands.");
        System.out.println("The initial prompt requests one read of pom.xml so tool activity is visible before the REPL starts.");
        System.out.println("Use /help for commands and /exit when the walkthrough is complete.");
    }

    private static String[] command(LiveExampleConfiguration configuration, String initialPrompt) {
        List<String> args = new ArrayList<>(List.of(
                "--tools", "read",
                "--model", configuration.model(),
                "--api-key", configuration.apiKey(),
                "--session-dir", configuration.sessionDirectory().toString()));
        configuration.baseUrl().ifPresent(baseUrl -> {
            args.add("--base-url");
            args.add(baseUrl);
        });
        args.add(initialPrompt);
        return args.toArray(String[]::new);
    }
}
