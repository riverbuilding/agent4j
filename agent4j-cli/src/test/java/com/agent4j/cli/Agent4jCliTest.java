package com.agent4j.cli;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.sdk.CodingAgentRuntimeServices;
import com.agent4j.coding.sdk.AgentSessionRuntime;
import com.agent4j.coding.sdk.CodingAgentSessionRuntime;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.testkit.ai.FakeModelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.util.List;
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

    @Test
    void printOptionRunsPromptAndWritesAssistantTextToConfiguredStdout() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueue(List.of(new AiStreamEvent.MessageCompleted(
                "assistant-1",
                new AiAssistantMessage(
                        List.of(new AiTextContent("command answer")),
                        AiStopReason.STOP,
                        AiUsage.zero()))));
        CliRuntimeFactory factory = request -> runtime(model);
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = Agent4jCli.execute(
                factory,
                environment(),
                new PrintWriter(stdout),
                new PrintWriter(stderr),
                "-p", "say hello");

        assertThat(exitCode).isZero();
        assertThat(stdout.toString()).isEqualTo("command answer\n");
        assertThat(stderr.toString()).isEmpty();
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
        return runtime(null);
    }

    private CliRuntime runtime(FakeModelClient model) throws Exception {
        CliEnvironment environment = environment();
        Files.createDirectories(environment.cwd());
        ResourceDiscovery discovery = new ResourceLoader().discover(
                ResourceDiscoveryOptions.enabled(environment.homeDirectory(), environment.cwd()));
        AgentSessionRuntime runtime = model == null
                ? new CodingAgentSessionRuntime()
                : new CodingAgentSessionRuntime(CodingAgentRuntimeServices.builder()
                        .modelClient(model)
                        .toolRegistry(InMemoryToolRegistry.builder().build())
                        .clock(Clock.systemUTC())
                        .build());
        return new CliRuntime(runtime, discovery, new AiModelReference("openai", "gpt-test"));
    }
}
