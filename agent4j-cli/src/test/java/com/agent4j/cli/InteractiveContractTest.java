package com.agent4j.cli;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderApi;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiToolCallContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.sdk.CodingAgentRuntimeServices;
import com.agent4j.coding.sdk.CodingAgentSessionRuntime;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.ToolRegistry;
import com.agent4j.core.tool.ToolSpec;
import com.agent4j.testkit.ai.FakeProvider;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end contract coverage for the observable Phase 11 terminal surface. */
class InteractiveContractTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @TempDir
    Path temporaryDirectory;

    @Test
    void fakeProviderTerminalCoversPromptStreamingToolExecutionAndPersistence() throws Exception {
        ToolCall toolCall = new ToolCall("call-1", "echo", JSON.objectNode().put("text", "tool output"));
        FakeProvider provider = fakeProvider()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.TextStarted("assistant-1", 0),
                        new AiStreamEvent.TextDelta("assistant-1", 0, "before tool"),
                        new AiStreamEvent.TextEnded("assistant-1", 0),
                        new AiStreamEvent.MessageCompleted("assistant-1", new AiAssistantMessage(
                                List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                                AiStopReason.TOOL_USE,
                                AiUsage.zero()))))
                .enqueue(List.of(new AiStreamEvent.MessageCompleted("assistant-2", new AiAssistantMessage(
                        List.of(new AiTextContent("final answer")), AiStopReason.STOP, AiUsage.zero()))));
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = run(
                runtime(provider, echoTools()),
                "use the tool\n/exit\n",
                stdout,
                stderr);

        assertThat(exitCode).isZero();
        assertThat(stdout.toString()).contains("before tool", "tool echo completed", "final answer");
        assertThat(stderr.toString()).isEmpty();
        assertThat(provider.requests()).hasSize(2);
        try (var sessions = Files.list(temporaryDirectory.resolve("sessions"))) {
            assertThat(sessions).anyMatch(path -> path.getFileName().toString().endsWith(".jsonl"));
        }
    }

    @Test
    void fakeProviderTerminalCoversCommandsModelSelectorSessionSelectorAndSessionReplacement() throws Exception {
        FakeProvider provider = fakeProvider();
        StringWriter stdout = new StringWriter();

        int exitCode = run(
                runtime(provider, InMemoryToolRegistry.builder().build()),
                "/help\n/model fake/second\n/status\n/new\n/continue\n/resume\n1\n/exit\n",
                stdout,
                new StringWriter());

        assertThat(exitCode).isZero();
        assertThat(stdout.toString()).contains("Commands:", "model: fake/second", "streaming: false", "Sessions:");
        try (var sessions = Files.list(temporaryDirectory.resolve("sessions"))) {
            assertThat(sessions.filter(path -> path.getFileName().toString().endsWith(".jsonl")).count()).isGreaterThanOrEqualTo(2);
        }
    }

    private int run(CliRuntime runtime, String input, StringWriter stdout, StringWriter stderr) {
        return new InteractiveModeRunner().run(
                runtime,
                new CliSessionLifecycle(runtime, environment(), new CliSessionOptions(
                        false, false, false, Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.of(temporaryDirectory.resolve("sessions")), Optional.empty())),
                new InteractiveTerminal(new StringReader(input), new PrintWriter(stdout), new PrintWriter(stderr)),
                List.of());
    }

    private CliRuntime runtime(AiProvider provider, ToolRegistry tools) throws Exception {
        Files.createDirectories(environment().cwd());
        ResourceDiscovery discovery = new ResourceLoader().discover(
                ResourceDiscoveryOptions.enabled(environment().homeDirectory(), environment().cwd()));
        var registry = com.agent4j.ai.AiProviderRegistry.builder()
                .add(provider)
                .defaultModel(new AiModelReference("fake", "first"))
                .build();
        return new CliRuntime(
                new CodingAgentSessionRuntime(CodingAgentRuntimeServices.builder()
                        .providerRegistry(registry)
                        .toolRegistry(tools)
                        .clock(Clock.systemUTC())
                        .build()),
                discovery,
                new AiModelReference("fake", "first"),
                Optional.of(registry));
    }

    private FakeProvider fakeProvider() {
        return new FakeProvider("fake", "Fake", AiProviderApi.CUSTOM, List.of(
                new AiModel(new AiModelReference("fake", "first"), "First"),
                new AiModel(new AiModelReference("fake", "second"), "Second")));
    }

    private static ToolRegistry echoTools() {
        return InMemoryToolRegistry.builder()
                .register(new ToolSpec("echo", "Echo", JSON.objectNode()), (call, context) ->
                        new ToolResult(call.id(), call.name(), false,
                                JSON.textNode(call.arguments().path("text").asText()), JSON.objectNode()))
                .build();
    }

    private CliEnvironment environment() {
        return new CliEnvironment(temporaryDirectory.resolve("workspace"), temporaryDirectory.resolve("home"));
    }

}
