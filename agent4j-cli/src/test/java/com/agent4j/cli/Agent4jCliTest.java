package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.sdk.AgentSessionRuntime;
import com.agent4j.coding.sdk.CodingAgentSessionRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class Agent4jCliTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void helpDoesNotCreateARuntime() {
        CliRuntimeFactory factory = request -> {
            throw new AssertionError("help must not create a runtime");
        };

        int exitCode = execute(factory, "--help");

        assertThat(exitCode).isZero();
    }

    @Test
    void rootCommandPassesParsedRuntimeInputsToInjectedFactory() throws Exception {
        AtomicReference<CliRuntimeRequest> received = new AtomicReference<>();
        CliRuntimeFactory factory = request -> {
            received.set(request);
            return runtime();
        };

        int exitCode = execute(
                factory,
                "--mode", "json",
                "--mode", "rpc",
                "--print",
                "--provider", "openai",
                "--model", "gpt-test",
                "--api-key", "sk-runtime-only",
                "hello");

        assertThat(exitCode).isEqualTo(1);
        assertThat(received.get()).isNotNull();
        assertThat(received.get().cwd()).isEqualTo(temporaryDirectory.resolve("workspace").toAbsolutePath());
        assertThat(received.get().provider()).contains("openai");
        assertThat(received.get().model()).contains("gpt-test");
        assertThat(received.get().apiKey()).contains("sk-runtime-only");
    }

    private CliEnvironment environment() {
        return new CliEnvironment(temporaryDirectory.resolve("workspace"), temporaryDirectory.resolve("home"));
    }

    private int execute(CliRuntimeFactory factory, String... args) {
        return Agent4jCli.execute(
                factory,
                environment(),
                new PrintWriter(new StringWriter()),
                new PrintWriter(new StringWriter()),
                args);
    }

    private CliRuntime runtime() throws Exception {
        CliEnvironment environment = environment();
        Files.createDirectories(environment.cwd());
        ResourceDiscovery discovery = new ResourceLoader().discover(
                ResourceDiscoveryOptions.enabled(environment.homeDirectory(), environment.cwd()));
        AgentSessionRuntime runtime = new CodingAgentSessionRuntime();
        return new CliRuntime(runtime, discovery, new AiModelReference("openai", "gpt-test"));
    }
}
