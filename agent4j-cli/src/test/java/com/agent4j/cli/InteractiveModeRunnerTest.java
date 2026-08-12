package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.sdk.CodingAgentRuntimeServices;
import com.agent4j.coding.sdk.CodingAgentSessionRuntime;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.testkit.ai.FakeModelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
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
        InteractiveModeRunner runner = new InteractiveModeRunner((session, received, initialMessages) -> {
            sessionId.set(session.id());
            receivedTerminal.set(received);
            return 7;
        });

        int exitCode = runner.run(runtime, new CliSessionLifecycle(runtime, environment, new CliSessionOptions(
                false, false, false, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(temporaryDirectory.resolve("sessions")), Optional.empty())), terminal, java.util.List.of());

        assertThat(exitCode).isEqualTo(7);
        assertThat(sessionId.get()).isNotBlank();
        assertThat(receivedTerminal.get()).isSameAs(terminal);
        assertThat(stdout.toString()).isEmpty();
        assertThat(stderr.toString()).isEmpty();
    }

    @Test
    void keepsOneSessionAcrossPromptsAndContinuesAfterPromptFailuresUntilEof() throws Exception {
        FakeModelClient model = new FakeModelClient()
                .enqueueFailure(new IllegalStateException("provider unavailable"))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.TextStarted("assistant-1", 0),
                        new AiStreamEvent.TextDelta("assistant-1", 0, "second "),
                        new AiStreamEvent.TextDelta("assistant-1", 0, "answer"),
                        new AiStreamEvent.TextEnded("assistant-1", 0),
                        new AiStreamEvent.MessageCompleted("assistant-1", new AiAssistantMessage(
                        List.of(new AiTextContent("second answer")), AiStopReason.STOP, AiUsage.zero()))));
        CliRuntime runtime = runtime(model);
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        InteractiveTerminal terminal = new InteractiveTerminal(
                new StringReader("first\n   \nsecond\n"), new PrintWriter(stdout), new PrintWriter(stderr));
        InteractiveModeRunner runner = new InteractiveModeRunner();

        int exitCode = runner.run(runtime, new CliSessionLifecycle(runtime, environment(), new CliSessionOptions(
                false, false, false, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(temporaryDirectory.resolve("sessions")), Optional.empty())), terminal, List.of());

        assertThat(exitCode).isZero();
        assertThat(model.requests()).hasSize(2);
        assertThat(stdout.toString()).isEqualTo("agent4j> agent4j> agent4j> second answer\nagent4j> ");
        assertThat(stderr.toString()).contains("provider unavailable");
        try (var files = Files.list(temporaryDirectory.resolve("sessions"))) {
            assertThat(files.filter(path -> path.getFileName().toString().endsWith(".jsonl")).count()).isEqualTo(1);
        }
    }

    private CliRuntime runtime() throws Exception {
        return runtime(null);
    }

    private CliRuntime runtime(FakeModelClient model) throws Exception {
        CliEnvironment environment = environment();
        Files.createDirectories(environment.cwd());
        ResourceDiscovery discovery = new ResourceLoader().discover(
                ResourceDiscoveryOptions.enabled(environment.homeDirectory(), environment.cwd()));
        CodingAgentRuntimeServices.Builder services = CodingAgentRuntimeServices.builder()
                .toolRegistry(InMemoryToolRegistry.builder().build())
                .clock(Clock.systemUTC());
        if (model != null) {
            services.modelClient(model);
        }
        return new CliRuntime(new CodingAgentSessionRuntime(services.build()), discovery, new AiModelReference("openai", "gpt-test"));
    }

    private CliEnvironment environment() {
        return new CliEnvironment(temporaryDirectory.resolve("workspace"), temporaryDirectory.resolve("home"));
    }
}
