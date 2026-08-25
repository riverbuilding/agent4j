package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.CodingAgentConfig;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CreateSessionRequest;
import com.agent4j.coding.sdk.PromptRequest;
import com.agent4j.coding.sdk.PromptResult;
import com.agent4j.coding.tool.CodingToolProfile;
import com.agent4j.coding.tool.CodingTools;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.event.EventSubscription;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in checks against the real provider APIs. Enable with
 * {@code -Dagent4j.liveSmoke=true}; credentials stay in process environment only.
 * Configure an OpenAI-compatible endpoint with {@code AGENT4J_API_KEY},
 * {@code AGENT4J_BASE_URL}, and {@code AGENT4J_MODEL}.
 */
@Tag("live")
@EnabledIfSystemProperty(named = "agent4j.liveSmoke", matches = "true")
class RealProviderSmokeTest {
    private static final int MAX_TOOL_ROUNDS = 8;
    private static final int MAX_OUTPUT_TOKENS = 512;
    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(90);
    private static final String TASK = "Fix Calculator.add, run /bin/sh test.sh to verify it, then summarize the result.";

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    @EnabledIfEnvironmentVariable(named = "AGENT4J_API_KEY", matches = ".+")
    void completesFixtureWithConfiguredEndpoint() throws Exception {
        runSmoke("openai", requiredEnvironment("AGENT4J_MODEL"), "AGENT4J_API_KEY",
                Optional.of(requiredEnvironment("AGENT4J_BASE_URL")));
    }

    private void runSmoke(String provider, String model, String credentialVariable, Optional<String> baseUrl) throws Exception {
        Path workspace = temporaryDirectory.resolve(provider + "-workspace");
        copyFixture(workspace);
        Map<Path, byte[]> before = fileContents(workspace);
        List<AgentEvent> events = new ArrayList<>();
        long startedAt = System.nanoTime();
        SmokeEvidence evidence = new SmokeEvidence(provider, model, Duration.ZERO, List.of(), List.of(), false);

        try {
            AgentEventBus eventBus = new AgentEventBus();
            CodingAgentConfig.Builder config = CodingAgentConfig.builder(
                            System.getenv(credentialVariable), model, workspace, temporaryDirectory.resolve(provider + "-sessions"))
                    .provider(provider)
                    .toolRegistry(CodingTools.localDefaults().registry(CodingToolProfile.FULL))
                    .eventBus(eventBus)
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .ownsWorkspace(true)
                    .ownsSessionDirectory(true);
            baseUrl.ifPresent(config::baseUrl);
            CodingAgentRuntime runtime = CodingAgentRuntime.create(config.build());
            AiModelReference modelReference = runtime.defaultModel();

            try (EventSubscription ignored = eventBus.subscribe(events::add);
                 AgentSession session = runtime.createSession(new CreateSessionRequest(
                         temporaryDirectory.resolve(provider + ".jsonl"), workspace, Optional.empty(), Optional.of(modelReference)))) {
                PromptResult result = session.prompt(new PromptRequest(
                        TASK,
                        Optional.of(modelReference),
                        MAX_TOOL_ROUNDS,
                        0,
                        Optional.of(MODEL_TIMEOUT),
                        Optional.empty(),
                        null,
                        Map.of(),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        Optional.empty(),
                        Optional.empty()));
                assertThat(result.loopResult().assistantMessages()).isNotEmpty();
            }

            List<String> toolCalls = events.stream()
                    .filter(AgentEvent.ToolExecutionStarted.class::isInstance)
                    .map(AgentEvent.ToolExecutionStarted.class::cast)
                    .map(event -> event.toolCall().name())
                    .toList();
            List<Path> changedFiles = changedFiles(before, workspace);
            boolean verified = events.stream()
                    .filter(AgentEvent.ToolExecutionEnded.class::isInstance)
                    .map(AgentEvent.ToolExecutionEnded.class::cast)
                    .filter(event -> event.result().toolName().equals("bash"))
                    .reduce((ignored, latest) -> latest)
                    .map(event -> event.result().content().path("exitCode").asInt(-1) == 0)
                    .orElse(false);
            evidence = new SmokeEvidence(provider, model, elapsedSince(startedAt), toolCalls,
                    changedFiles.stream().map(workspace::relativize).map(Path::toString).toList(), verified);

            assertThat(Files.readString(workspace.resolve("Calculator.java"))).contains("return left + right;");
            assertThat(changedFiles).contains(workspace.resolve("Calculator.java"));
            assertThat(verified).isTrue();
        } finally {
            System.out.println(evidence);
        }
    }

    private static String requiredEnvironment(String variable) {
        return Optional.ofNullable(System.getenv(variable))
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException(variable + " must be set when AGENT4J_API_KEY is set"));
    }

    private static Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private static Map<Path, byte[]> fileContents(Path directory) throws Exception {
        Map<Path, byte[]> contents = new LinkedHashMap<>();
        try (var files = Files.walk(directory)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                contents.put(directory.relativize(path), Files.readAllBytes(path));
            }
        }
        return contents;
    }

    private static List<Path> changedFiles(Map<Path, byte[]> before, Path workspace) throws Exception {
        Map<Path, byte[]> after = fileContents(workspace);
        return after.entrySet().stream()
                .filter(entry -> !java.util.Arrays.equals(before.get(entry.getKey()), entry.getValue()))
                .map(entry -> workspace.resolve(entry.getKey()))
                .toList();
    }

    private void copyFixture(Path destination) throws Exception {
        URI fixture = getClass().getResource("/mini-agent-fixture").toURI();
        try (var files = Files.walk(Path.of(fixture))) {
            for (Path source : files.toList()) {
                Path target = destination.resolve(Path.of(fixture).relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target);
                }
            }
        }
    }

    private record SmokeEvidence(
            String provider,
            String model,
            Duration elapsed,
            List<String> toolCalls,
            List<String> changedFiles,
            boolean verified
    ) {
    }
}
