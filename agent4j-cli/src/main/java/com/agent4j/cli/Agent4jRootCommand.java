package com.agent4j.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

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

    @Parameters(arity = "0..*", paramLabel = "messages", description = "Initial prompt text")
    private List<String> messages = List.of();

    Agent4jRootCommand(CliRuntimeFactory runtimeFactory, CliEnvironment environment) {
        this.runtimeFactory = runtimeFactory;
        this.environment = environment;
    }

    @Override
    public Integer call() throws Exception {
        runtimeFactory.create(runtimeRequest());
        throw new IllegalStateException("no runnable CLI mode is implemented yet; Phase 10 Slice 3 adds print mode");
    }

    CliRuntimeRequest runtimeRequest() {
        return new CliRuntimeRequest(
                environment.cwd(),
                environment.homeDirectory(),
                Optional.ofNullable(provider),
                Optional.ofNullable(model),
                Optional.ofNullable(apiKey));
    }

    CliMode mode() {
        return modes.isEmpty() ? null : modes.getLast();
    }

    boolean print() {
        return print;
    }

    List<String> messages() {
        return List.copyOf(messages);
    }
}
