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

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveSessionControllerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndResumesSessionsThroughTheCliLifecycle() throws Exception {
        CliRuntime runtime = runtime();
        CliSessionLifecycle lifecycle = new CliSessionLifecycle(runtime, environment(), new CliSessionOptions(
                false, false, false, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(temporaryDirectory.resolve("sessions")), Optional.empty()));
        var first = lifecycle.open();
        InteractiveTerminal terminal = new InteractiveTerminal(new StringReader(""), new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter()));

        try (InteractiveSessionController controller = new InteractiveSessionController(runtime, lifecycle, first, terminal)) {
            controller.createNew();
            String createdId = controller.session().id();
            assertThat(createdId).isNotEqualTo(first.id());

            controller.resume(first.sessionFile().toString());
            assertThat(controller.session().id()).isEqualTo(first.id());
        }
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
