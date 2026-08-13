package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.sdk.CodingAgentSessionRuntime;
import com.agent4j.coding.sdk.CreateSessionRequest;
import com.agent4j.coding.sdk.CodingAgentRuntimeServices;
import com.agent4j.coding.session.SessionManager;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.testkit.ai.FakeModelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliSessionLifecycleTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsResumesAndForksPersistentSessionsThroughSdkRuntime() throws Exception {
        CliRuntime runtime = runtime();
        Path directory = temporaryDirectory.resolve("sessions");
        Agent4jSession first = new Agent4jSession(new CliSessionLifecycle(runtime, environment(), options(directory)).open());

        Agent4jSession resumed = new Agent4jSession(new CliSessionLifecycle(runtime, environment(), new CliSessionOptions(
                false, false, false, Optional.of(first.file().toString()), Optional.empty(), Optional.empty(), Optional.of(directory), Optional.empty())).open());
        Agent4jSession forked = new Agent4jSession(new CliSessionLifecycle(runtime, environment(), new CliSessionOptions(
                false, false, false, Optional.empty(), Optional.empty(), Optional.of(first.file().toString()), Optional.of(directory), Optional.empty())).open());

        assertThat(first.file()).exists();
        assertThat(resumed.id()).isEqualTo(first.id());
        assertThat(forked.id()).isNotEqualTo(first.id());
        assertThat(forked.file()).exists();
    }

    @Test
    void continuesMostRecentSessionAndRejectsConflictingFlags() throws Exception {
        CliRuntime runtime = runtime();
        Path directory = temporaryDirectory.resolve("sessions");
        Agent4jSession first = new Agent4jSession(new CliSessionLifecycle(runtime, environment(), options(directory)).open());

        Agent4jSession continued = new Agent4jSession(new CliSessionLifecycle(runtime, environment(), new CliSessionOptions(
                true, false, false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(directory), Optional.empty())).open());

        assertThat(continued.id()).isEqualTo(first.id());
        assertThatThrownBy(() -> new CliSessionLifecycle(runtime, environment(), new CliSessionOptions(
                true, false, false, Optional.of(first.file().toString()), Optional.empty(), Optional.of(first.file().toString()), Optional.of(directory), Optional.empty())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--fork cannot be combined");
    }

    @Test
    void findsGlobalSessionsAndForksAfterCrossProjectConfirmation() throws Exception {
        CliRuntime runtime = runtime();
        Path otherProject = temporaryDirectory.resolve("other-project");
        Path globalFile = environment().homeDirectory().resolve(".pi/agent/sessions/other/session.jsonl");
        SessionManager.create(globalFile, otherProject, "global-session");
        CliSessionLifecycle lifecycle = new CliSessionLifecycle(runtime, environment(), options(temporaryDirectory.resolve("sessions")));

        assertThat(lifecycle.candidates()).anyMatch(candidate -> candidate.id().equals("global-session"));
        assertThat(lifecycle.isCrossProject("global-session")).isTrue();
        var forked = lifecycle.resume("global-session", true);

        assertThat(forked.cwd()).isEqualTo(environment().cwd());
        assertThat(forked.id()).isNotEqualTo("global-session");
    }

    private CliSessionOptions options(Path directory) {
        return new CliSessionOptions(false, false, false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(directory), Optional.of("feature-work"));
    }

    private CliRuntime runtime() throws Exception {
        CliEnvironment environment = environment();
        Files.createDirectories(environment.cwd());
        ResourceDiscovery discovery = new ResourceLoader().discover(ResourceDiscoveryOptions.enabled(environment.homeDirectory(), environment.cwd()));
        return new CliRuntime(new CodingAgentSessionRuntime(CodingAgentRuntimeServices.builder()
                .modelClient(new FakeModelClient())
                .toolRegistry(InMemoryToolRegistry.builder().build())
                .clock(Clock.systemUTC())
                .build()), discovery, new AiModelReference("openai", "gpt-test"));
    }

    private CliEnvironment environment() {
        return new CliEnvironment(temporaryDirectory.resolve("workspace"), temporaryDirectory.resolve("home"));
    }

    private record Agent4jSession(com.agent4j.coding.sdk.AgentSession delegate) {
        String id() { return delegate.id(); }
        Path file() { return delegate.sessionFile(); }
    }
}
