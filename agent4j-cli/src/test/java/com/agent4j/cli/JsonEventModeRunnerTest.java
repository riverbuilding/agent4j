package com.agent4j.cli;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.core.runtime.AbortController;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.testkit.ai.FakeModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JsonEventModeRunnerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesSessionHeaderAndEventsInRuntimeOrder() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueue(List.of(
                new AiStreamEvent.MessageStarted("assistant-1"),
                new AiStreamEvent.TextStarted("assistant-1", 0),
                new AiStreamEvent.TextDelta("assistant-1", 0, "hello"),
                new AiStreamEvent.TextEnded("assistant-1", 0),
                new AiStreamEvent.MessageCompleted("assistant-1", new AiAssistantMessage(
                        List.of(new AiTextContent("hello")), AiStopReason.STOP, AiUsage.zero()))));
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = run(model, Optional.empty(), stdout, stderr);
        List<JsonNode> lines = lines(stdout);

        assertThat(exitCode).withFailMessage(stderr.toString()).isZero();
        assertThat(stderr.toString()).isEmpty();
        assertThat(lines).extracting(line -> line.path("type").asText())
                .containsExactly("session", "agent_start", "turn_start", "message_start", "message_end",
                        "message_start", "message_update", "message_update", "message_update", "message_end",
                        "turn_end", "agent_end");
        assertThat(lines.getFirst().path("version").asInt()).isEqualTo(3);
        assertThat(lines.get(7).path("assistantMessageEvent").path("type").asText()).isEqualTo("text_delta");
        assertThat(lines.get(7).path("assistantMessageEvent").path("delta").asText()).isEqualTo("hello");
        assertThat(lines.get(7).path("message").path("content").get(0).path("text").asText()).isEqualTo("hello");
        assertThat(lines.get(9).path("message").path("content").get(0).path("text").asText()).isEqualTo("hello");
        assertThat(noTemporarySessions()).isTrue();
    }

    @Test
    void reportsProviderFailureOnlyToStderrAfterTheHeaderAndStartedEvents() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueueFailure(new IllegalStateException("provider unavailable"));
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = run(model, Optional.empty(), stdout, stderr);

        assertThat(exitCode).isEqualTo(1);
        assertThat(lines(stdout)).extracting(line -> line.path("type").asText())
                .containsExactly("session", "agent_start", "turn_start", "message_start", "message_end");
        assertThat(stderr.toString()).contains("provider unavailable");
    }

    @Test
    void reportsPreAbortedRequestToStderrWithoutHumanStdout() throws Exception {
        AbortController controller = new AbortController();
        controller.abort("cancelled by test");
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = run(new FakeModelClient(), Optional.of(controller.signal()), stdout, stderr);

        assertThat(exitCode).isEqualTo(1);
        assertThat(lines(stdout)).extracting(line -> line.path("type").asText())
                .containsExactly("session");
        assertThat(stderr.toString()).contains("cancelled by test");
    }

    private int run(
            FakeModelClient model,
            Optional<com.agent4j.core.runtime.AbortSignal> abortSignal,
            StringWriter stdout,
            StringWriter stderr
    ) throws Exception {
        return new JsonEventModeRunner(temporaryDirectory, new JsonEventSerializer()).run(
                runtime(model),
                environment(),
                List.of("say hello"),
                abortSignal,
                new PrintWriter(stdout),
                new PrintWriter(stderr));
    }

    private CliRuntime runtime(FakeModelClient model) throws Exception {
        CliEnvironment environment = environment();
        Files.createDirectories(environment.cwd());
        ResourceDiscovery discovery = new ResourceLoader().discover(
                ResourceDiscoveryOptions.enabled(environment.homeDirectory(), environment.cwd()));
        return new CliRuntime(
                CodingAgentRuntime.builder()
                        .modelClient(model)
                        .toolRegistry(InMemoryToolRegistry.builder().build())
                        .clock(Clock.systemUTC())
                        .build(),
                discovery,
                new AiModelReference("openai", "gpt-test"));
    }

    private CliEnvironment environment() {
        return new CliEnvironment(temporaryDirectory.resolve("workspace"), temporaryDirectory.resolve("home"));
    }

    private List<JsonNode> lines(StringWriter output) throws Exception {
        java.util.ArrayList<JsonNode> lines = new java.util.ArrayList<>();
        for (String line : output.toString().lines().toList()) {
            lines.add(JSON.readTree(line));
        }
        return List.copyOf(lines);
    }

    private boolean noTemporarySessions() throws Exception {
        try (var paths = Files.list(temporaryDirectory)) {
            return paths.noneMatch(path -> path.getFileName().toString().startsWith("agent4j-json-"));
        }
    }
}
