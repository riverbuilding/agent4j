package com.agent4j.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(
        name = "agent4j",
        mixinStandardHelpOptions = true,
        version = "agent4j 0.1.0-SNAPSHOT",
        description = "Java PI-compatible coding agent."
)
public final class Agent4jRootCommand implements Callable<Integer> {
    private final CliRuntimeFactory runtimeFactory;
    private final CliEnvironment environment;
    private final PrintModeRunner printModeRunner;
    private final JsonEventModeRunner jsonEventModeRunner;
    private final RpcModeRunner rpcModeRunner;
    private final Reader input;

    @Spec
    private CommandSpec commandSpec;

    @Option(
            names = "--mode",
            paramLabel = "mode",
            converter = CliModeConverter.class,
            description = "Output mode: text, json, or rpc")
    private List<CliMode> modes = new ArrayList<>();

    @Option(names = {"--print", "-p"}, description = "Run a prompt and exit")
    private boolean print;

    @Option(names = "--provider", description = "LLM provider ID")
    private String provider;

    @Option(names = "--model", description = "Model ID or provider/model")
    private String model;

    @Option(names = "--api-key", description = "Non-persistent provider API key")
    private String apiKey;

    @Option(names = {"--tools", "-t"}, split = ",", description = "Comma-separated enabled tool names")
    private List<String> includedTools = new ArrayList<>();
    @Option(names = {"--exclude-tools", "-xt"}, split = ",", description = "Comma-separated disabled tool names")
    private List<String> excludedTools = new ArrayList<>();
    @Option(names = {"--no-tools", "-nt"}, description = "Disable all tools") private boolean noTools;
    @Option(names = {"--no-builtin-tools", "-nbt"}, description = "Disable built-in tools") private boolean noBuiltinTools;

    @Option(names = {"--continue", "-c"}) private boolean continueSession;
    @Option(names = {"--resume", "-r"}) private boolean resume;
    @Option(names = "--no-session") private boolean noSession;
    @Option(names = "--session") private String session;
    @Option(names = "--session-id") private String sessionId;
    @Option(names = "--fork") private String fork;
    @Option(names = "--session-dir") private java.nio.file.Path sessionDirectory;
    @Option(names = {"--name", "-n"}) private String name;

    @Parameters(arity = "0..*", paramLabel = "messages", description = "Initial prompt text")
    private List<String> messages = List.of();

    Agent4jRootCommand(CliRuntimeFactory runtimeFactory, CliEnvironment environment) {
        this(runtimeFactory, environment, new PrintModeRunner(), new JsonEventModeRunner(), new RpcModeRunner(),
                new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
    }

    Agent4jRootCommand(CliRuntimeFactory runtimeFactory, CliEnvironment environment, PrintModeRunner printModeRunner) {
        this(runtimeFactory, environment, printModeRunner, new JsonEventModeRunner(), new RpcModeRunner(),
                new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
    }

    Agent4jRootCommand(
            CliRuntimeFactory runtimeFactory,
            CliEnvironment environment,
            PrintModeRunner printModeRunner,
            JsonEventModeRunner jsonEventModeRunner,
            RpcModeRunner rpcModeRunner,
            Reader input
    ) {
        this.runtimeFactory = runtimeFactory;
        this.environment = environment;
        this.printModeRunner = printModeRunner;
        this.jsonEventModeRunner = jsonEventModeRunner;
        this.rpcModeRunner = rpcModeRunner;
        this.input = input;
    }

    @Override
    public Integer call() throws Exception {
        CliRuntime runtime = runtimeFactory.create(runtimeRequest());
        CliSessionLifecycle sessions = new CliSessionLifecycle(runtime, environment, sessionOptions());
        try {
            if (mode() == CliMode.RPC) {
                return rpcModeRunner.run(runtime, environment, input, commandSpec.commandLine().getOut(), commandSpec.commandLine().getErr(), sessions);
            }
            if (mode() == CliMode.JSON) {
                return jsonEventModeRunner.run(runtime, environment, messages, Optional.empty(), commandSpec.commandLine().getOut(), commandSpec.commandLine().getErr(), sessions);
            }
            if (print) {
                return printModeRunner.run(runtime, environment, messages, Optional.empty(), commandSpec.commandLine().getOut(), commandSpec.commandLine().getErr(), sessions);
            }
            throw new IllegalStateException("requested CLI mode is not implemented yet; Phase 10 Slice 6 adds session lifecycle selection");
        } finally {
            sessions.close();
        }
    }

    CliRuntimeRequest runtimeRequest() {
        return new CliRuntimeRequest(
                environment.cwd(),
                environment.homeDirectory(),
                Optional.ofNullable(provider),
                Optional.ofNullable(model),
                Optional.ofNullable(apiKey),
                new CliToolSelection(
                        includedTools.isEmpty() ? Optional.empty() : Optional.of(includedTools),
                        excludedTools,
                        noTools,
                        noBuiltinTools));
    }

    CliMode mode() {
        return modes.isEmpty() ? null : modes.getLast();
    }

    private CliSessionOptions sessionOptions() {
        return new CliSessionOptions(continueSession, resume, noSession, Optional.ofNullable(session), Optional.ofNullable(sessionId),
                Optional.ofNullable(fork), Optional.ofNullable(sessionDirectory), Optional.ofNullable(name));
    }

    boolean print() {
        return print;
    }

    List<String> messages() {
        return List.copyOf(messages);
    }
}
