package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.sdk.CodingAgentRuntimeServices;
import com.agent4j.coding.sdk.CodingAgentSessionRuntime;
import com.agent4j.core.tool.InMemoryToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveModeRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void opensTheLifecycleSessionAndPassesInjectedTerminalToHost() throws Exception {
        CliRuntime runtime = runtime();
        CliEnvironment environment = environment();
        StringReader input = new StringReader("interactive input");
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        InteractiveTerminal terminal = new InteractiveTerminal(input, new PrintWriter(stdout), new PrintWriter(stderr));
        AtomicReference<String> sessionId = new AtomicReference<>();
        AtomicReference<InteractiveTerminal> receivedTerminal = new AtomicReference<>();
        InteractiveModeRunner runner = new InteractiveModeRunner((session, received) -> {
            sessionId.set(session.id());
            receivedTerminal.set(received);
            return 7;
        });

        int exitCode = runner.run(runtime, new CliSessionLifecycle(runtime, environment, new CliSessionOptions(
                false, false, false, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(temporaryDirectory.resolve("sessions")), Optional.empty())), terminal);

        assertThat(exitCode).isEqualTo(7);
        assertThat(sessionId.get()).isNotBlank();
        assertThat(receivedTerminal.get()).isSameAs(terminal);
        assertThat(stdout.toString()).isEmpty();
        assertThat(stderr.toString()).isEmpty();
    }

    private CliRuntime runtime() throws Exception {
        CliEnvironment environment = environment();
        Files.createDirectories(environment.cwd());
        ResourceDiscovery discovery = new ResourceLoader().discover(
                ResourceDiscoveryOptions.enabled(environment.homeDirectory(), environment.cwd()));
        return new CliRuntime(new CodingAgentSessionRuntime(CodingAgentRuntimeServices.builder()
                .toolRegistry(InMemoryToolRegistry.builder().build())
                .clock(Clock.systemUTC())
                .build()), discovery, new AiModelReference("openai", "gpt-test"));
    }

    private CliEnvironment environment() {
        return new CliEnvironment(temporaryDirectory.resolve("workspace"), temporaryDirectory.resolve("home"));
    }
}
