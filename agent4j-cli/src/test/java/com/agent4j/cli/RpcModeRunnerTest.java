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
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.testkit.ai.FakeModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RpcModeRunnerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void acknowledgesPromptBeforeStreamingItsEvents() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueue(List.of(new AiStreamEvent.MessageCompleted(
                "assistant-1",
                new AiAssistantMessage(List.of(new AiTextContent("hello")), AiStopReason.STOP, AiUsage.zero()))));
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = run(model, "{\"id\":\"prompt-1\",\"type\":\"prompt\",\"message\":\"Hello\"}\n", stdout, stderr);
        List<JsonNode> lines = lines(stdout);

        assertThat(exitCode).withFailMessage(stderr.toString()).isZero();
        assertThat(lines.getFirst().path("type").asText()).isEqualTo("response");
        assertThat(lines.getFirst().path("id").asText()).isEqualTo("prompt-1");
        assertThat(lines.getFirst().path("command").asText()).isEqualTo("prompt");
        assertThat(lines.getFirst().path("success").asBoolean()).isTrue();
        assertThat(lines).extracting(line -> line.path("type").asText())
                .containsSubsequence("agent_start", "turn_start", "message_end", "turn_end", "agent_end");
        assertThat(stderr.toString()).isEmpty();
        assertThat(noTemporarySessions()).isTrue();
    }

    @Test
    void keepsRunningAfterMalformedAndUnsupportedRequestsAndPreservesIds() throws Exception {
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = run(new FakeModelClient(), """
                not-json
                {"id":"unknown-1","type":"not_supported"}
                {"id":"shutdown-1","type":"shutdown"}
                """, stdout, stderr);
        List<JsonNode> lines = lines(stdout);

        assertThat(exitCode).isZero();
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).path("command").asText()).isEqualTo("parse");
        assertThat(lines.get(0).path("success").asBoolean()).isFalse();
        assertThat(lines.get(1).path("id").asText()).isEqualTo("unknown-1");
        assertThat(lines.get(1).path("command").asText()).isEqualTo("not_supported");
        assertThat(lines.get(1).path("success").asBoolean()).isFalse();
        assertThat(lines.get(2).path("id").asText()).isEqualTo("shutdown-1");
        assertThat(lines.get(2).path("success").asBoolean()).isTrue();
        assertThat(stderr.toString()).isEmpty();
    }

    @Test
    void supportsSessionStateMessagesNameAndNewSessionCommands() throws Exception {
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = run(new FakeModelClient(), """
                {"id":"name","type":"set_session_name","name":"feature-work"}
                {"id":"state","type":"get_state"}
                {"id":"messages","type":"get_messages"}
                {"id":"new","type":"new_session"}
                {"id":"shutdown","type":"shutdown"}
                """, stdout, stderr);
        List<JsonNode> lines = lines(stdout);

        assertThat(exitCode).isZero();
        assertThat(lines).extracting(line -> line.path("command").asText())
                .containsExactly("set_session_name", "get_state", "get_messages", "new_session", "shutdown");
        assertThat(lines.get(1).path("data").path("sessionName").asText()).isEqualTo("feature-work");
        assertThat(lines.get(1).path("data").path("isStreaming").asBoolean()).isFalse();
        assertThat(lines.get(2).path("data").path("messages")).isEmpty();
        assertThat(lines.get(3).path("data").path("cancelled").asBoolean()).isFalse();
        assertThat(stderr.toString()).isEmpty();
    }

    @Test
    void preservesPersistentSessionDirectoryOwnedByLifecycle() throws Exception {
        Path sessionDirectory = temporaryDirectory.resolve("sessions");
        Files.createDirectories(sessionDirectory);
        Path sentinel = sessionDirectory.resolve("keep.txt");
        Files.writeString(sentinel, "preserve me");
        CliRuntime runtime = runtime(new FakeModelClient());
        CliSessionLifecycle lifecycle = new CliSessionLifecycle(runtime, environment(), new CliSessionOptions(
                false, false, false, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.of(sessionDirectory), java.util.Optional.empty()));
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = new RpcModeRunner(temporaryDirectory, JSON, new JsonEventSerializer()).run(
                runtime, environment(), new StringReader("{\"id\":\"shutdown\",\"type\":\"shutdown\"}\n"),
                new PrintWriter(stdout), new PrintWriter(stderr), lifecycle);

        assertThat(exitCode).withFailMessage(stderr.toString()).isZero();
        assertThat(sentinel).exists();
        assertThat(sessionDirectory).isDirectory();
    }

    private int run(FakeModelClient model, String input, StringWriter stdout, StringWriter stderr) throws Exception {
        return new RpcModeRunner(temporaryDirectory, JSON, new JsonEventSerializer()).run(
                runtime(model),
                environment(),
                new StringReader(input),
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
            return paths.noneMatch(path -> path.getFileName().toString().startsWith("agent4j-rpc-"));
        }
    }
}
