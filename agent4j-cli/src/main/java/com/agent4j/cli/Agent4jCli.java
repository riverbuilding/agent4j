package com.agent4j.cli;

import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class Agent4jCli {
    private Agent4jCli() {
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    public static int execute(String... args) {
        return execute(new DefaultCliRuntimeFactory(), CliEnvironment.system(), args);
    }

    /** Executes the CLI with caller-provided standard streams. */
    public static int execute(Reader input, PrintWriter out, PrintWriter err, String... args) {
        return execute(new DefaultCliRuntimeFactory(), CliEnvironment.system(), input, out, err, args);
    }

    static int execute(CliRuntimeFactory runtimeFactory, CliEnvironment environment, String... args) {
        return execute(runtimeFactory, environment, new PrintWriter(System.out, true), new PrintWriter(System.err, true), args);
    }

    static int execute(
            CliRuntimeFactory runtimeFactory,
            CliEnvironment environment,
            PrintWriter out,
            PrintWriter err,
            String... args
    ) {
        return execute(runtimeFactory, environment, new InputStreamReader(System.in, StandardCharsets.UTF_8), out, err, args);
    }

    static int execute(
            CliRuntimeFactory runtimeFactory,
            CliEnvironment environment,
            Reader input,
            PrintWriter out,
            PrintWriter err,
            String... args
    ) {
        Agent4jRootCommand command = new Agent4jRootCommand(
                runtimeFactory, environment, new PrintModeRunner(), new JsonEventModeRunner(), new RpcModeRunner(), new InteractiveModeRunner(), input);
        CommandLine commandLine = new CommandLine(command);
        commandLine.setOut(out);
        commandLine.setErr(err);
        commandLine.setExecutionExceptionHandler((exception, ignored, parseResult) -> {
            parseResult.commandSpec().commandLine().getErr().println("Error: " + exception.getMessage());
            return 1;
        });
        return commandLine.execute(args);
    }
}
