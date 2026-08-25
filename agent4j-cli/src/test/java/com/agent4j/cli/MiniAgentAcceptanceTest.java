package com.agent4j.cli;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderRegistry;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiToolCallContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.tool.CodingToolProfile;
import com.agent4j.coding.tool.CodingTools;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.event.EventSubscription;
import com.agent4j.testkit.ai.FakeModelClient;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniAgentAcceptanceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void completesASimpleCodingTaskThroughTheCliRuntime() throws Exception {
        Path workspace = temporaryDirectory.resolve("workspace");
        copyFixture(workspace);
        FakeModelClient model = scriptedModel();
        AgentEventBus eventBus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        CliEnvironment environment = new CliEnvironment(workspace, temporaryDirectory.resolve("home"));
        AiModelReference modelReference = new AiModelReference("fake", "mini-agent");
        AiProviderRegistry providers = AiProviderRegistry.fixedClient(new AiModel(modelReference, "Mini agent"), model);
        CodingAgentRuntime runtime = CodingAgentRuntime.builder()
                .eventBus(eventBus)
                .providerRegistry(providers)
                .toolRegistry(CodingTools.localDefaults().registry(CodingToolProfile.FULL))
                .clock(Clock.systemUTC())
                .build();
        ResourceDiscovery discovery = new ResourceLoader().discover(
                ResourceDiscoveryOptions.enabled(environment.homeDirectory(), workspace));

        try (EventSubscription ignored = eventBus.subscribe(events::add)) {
            int exitCode = Agent4jCli.execute(
                    request -> new CliRuntime(runtime, discovery, modelReference, java.util.Optional.of(providers)),
                    environment,
                    new StringReader(""),
                    new PrintWriter(stdout),
                    new PrintWriter(stderr),
                    "--print", "Fix Calculator.add and verify the test.");

            assertThat(exitCode).isZero();
        }

        assertThat(Files.readString(workspace.resolve("Calculator.java"))).contains("return left + right;");
        assertThat(stdout.toString()).isEqualTo("Implemented Calculator.add and verified test.sh passes.\n");
        assertThat(stderr.toString()).isEmpty();
        assertThat(events).filteredOn(AgentEvent.ToolExecutionStarted.class::isInstance)
                .extracting(event -> ((AgentEvent.ToolExecutionStarted) event).toolCall().name())
                .containsExactly("read", "bash", "edit", "bash");
        assertThat(events).filteredOn(AgentEvent.ToolExecutionEnded.class::isInstance)
                .extracting(event -> ((AgentEvent.ToolExecutionEnded) event).result().content().path("exitCode").asInt())
                .containsExactly(0, 1, 0, 0);
        assertThat(events).filteredOn(AgentEvent.ToolExecutionEnded.class::isInstance)
                .map(event -> ((AgentEvent.ToolExecutionEnded) event).result())
                .filteredOn(result -> result.toolName().equals("bash"))
                .extracting(result -> result.content().path("stderr").asText())
                .first()
                .asString()
                .contains("add should sum its arguments");
        Path sessionDirectory = environment.homeDirectory().resolve(".pi/agent/sessions");
        try (var files = Files.walk(sessionDirectory)) {
            List<Path> sessions = files.filter(path -> path.getFileName().toString().endsWith(".jsonl")).toList();
            assertThat(sessions).hasSize(1);
            assertThat(Files.readString(sessions.getFirst())).contains("Calculator.java", "verified test.sh passes");
        }
    }

    private static FakeModelClient scriptedModel() {
        return new FakeModelClient()
                .enqueue(toolCall("read-1", "read", JsonNodeFactory.instance.objectNode().put("path", "Calculator.java")))
                .enqueue(toolCall("test-before", "bash", JsonNodeFactory.instance.objectNode().put("command", "/bin/sh test.sh")))
                .enqueue(toolCall("edit-1", "edit", JsonNodeFactory.instance.objectNode()
                        .put("path", "Calculator.java")
                        .put("oldText", "return left - right;")
                        .put("newText", "return left + right;")))
                .enqueue(toolCall("test-after", "bash", JsonNodeFactory.instance.objectNode().put("command", "/bin/sh test.sh")))
                .enqueue(List.of(new AiStreamEvent.MessageCompleted(
                        "assistant-final",
                        new AiAssistantMessage(
                                List.of(new AiTextContent("Implemented Calculator.add and verified test.sh passes.")),
                                AiStopReason.STOP,
                                AiUsage.zero()))));
    }

    private static List<AiStreamEvent> toolCall(String id, String name, com.fasterxml.jackson.databind.JsonNode arguments) {
        return List.of(new AiStreamEvent.MessageCompleted(
                "assistant-" + id,
                new AiAssistantMessage(List.of(new AiToolCallContent(id, name, arguments)), AiStopReason.TOOL_USE, AiUsage.zero())));
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
}
