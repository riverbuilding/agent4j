package com.agent4j.examples;

import com.agent4j.cli.Agent4jCli;

import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 10-cli-modes: invokes composed print, JSON, RPC, resume, and fork CLI commands. */
public final class CliModesExample {
    private CliModesExample() {
    }

    public static void main(String[] args) throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open();
        try {
            Files.createDirectories(configuration.sessionDirectory());

            run("print", command(configuration,
                    "--print",
                    "Reply with exactly: print mode complete."));
            Path sourceSession = mostRecentSession(configuration.sessionDirectory());

            run("json", command(configuration,
                    "--mode", "json",
                    "Reply with exactly: json mode complete."));

            runRpc(configuration);

            run("resume", command(configuration,
                    "--print",
                    "--session", sourceSession.toString(),
                    "Reply with exactly: resumed session complete."));

            run("fork", command(configuration,
                    "--print",
                    "--fork", sourceSession.toString(),
                    "Reply with exactly: forked session complete."));
        } finally {
            configuration.cleanupTemporaryDirectories();
        }
    }

    private static void runRpc(LiveExampleConfiguration configuration) {
        String input = """
                {"id":1,"type":"get_state"}
                {"id":2,"type":"prompt","message":"Reply with exactly: rpc mode complete."}
                """;
        System.out.println("--- rpc ---");
        int exitCode = Agent4jCli.execute(
                new StringReader(input),
                new PrintWriter(System.out, true),
                new PrintWriter(System.err, true),
                command(configuration, "--mode", "rpc"));
        requireSuccess("rpc", exitCode);
    }

    private static void run(String name, String[] args) {
        System.out.println("--- " + name + " ---");
        requireSuccess(name, Agent4jCli.execute(args));
    }

    private static String[] command(LiveExampleConfiguration configuration, String... command) {
        List<String> args = new ArrayList<>(List.of(
                "--no-tools",
                "--model", configuration.model(),
                "--api-key", configuration.apiKey(),
                "--session-dir", configuration.sessionDirectory().toString()));
        configuration.baseUrl().ifPresent(baseUrl -> {
            args.add("--base-url");
            args.add(baseUrl);
        });
        args.addAll(List.of(command));
        return args.toArray(String[]::new);
    }

    private static Path mostRecentSession(Path sessionDirectory) throws Exception {
        try (var files = Files.list(sessionDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .max(Comparator.comparing(path -> {
                        try {
                            return Files.getLastModifiedTime(path);
                        } catch (java.io.IOException error) {
                            throw new IllegalStateException("cannot read session modification time", error);
                        }
                    }))
                    .orElseThrow(() -> new IllegalStateException("print mode did not create a session"));
        }
    }

    private static void requireSuccess(String command, int exitCode) {
        if (exitCode != 0) {
            throw new IllegalStateException(command + " command exited with code " + exitCode);
        }
    }
}
