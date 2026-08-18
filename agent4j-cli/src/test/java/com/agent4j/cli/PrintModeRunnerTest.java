package com.agent4j.cli;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiSystemMessage;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiToolCallContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.runtime.AbortController;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.ToolRegistry;
import com.agent4j.core.tool.ToolSpec;
import com.agent4j.testkit.ai.FakeModelClient;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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

class PrintModeRunnerTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesFinalAssistantTextToStdout() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueue(assistantText("assistant-1", "print answer"));
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = run(runtime(model, emptyTools()), List.of("say", "hello"), Optional.empty(), stdout, stderr);

        assertThat(exitCode).withFailMessage(stderr.toString()).isZero();
        assertThat(stdout.toString()).isEqualTo("print answer\n");
        assertThat(stderr.toString()).isEmpty();
        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().getFirst().messages())
                .filteredOn(AiSystemMessage.class::isInstance)
                .extracting(AiSystemMessage.class::cast)
                .extracting(AiSystemMessage::content)
                .singleElement()
                .asString()
                .contains("agent4j-coding-v1");
        assertThat(noTemporarySessions()).isTrue();
    }

    @Test
    void waitsForToolRoundAndPrintsFinalAssistantText() throws Exception {
        ToolCall toolCall = new ToolCall("tool-1", "echo", JSON.objectNode().put("text", "tool output"));
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(new AiStreamEvent.MessageCompleted(
                        "assistant-tool",
                        new AiAssistantMessage(
                                List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                                AiStopReason.TOOL_USE,
                                AiUsage.zero()))))
                .enqueue(assistantText("assistant-final", "tool round complete"));
        ToolRegistry tools = InMemoryToolRegistry.builder()
                .register(new ToolSpec("echo", "Echo", JSON.objectNode()), (call, context) ->
                        new ToolResult(call.id(), call.name(), false, JSON.textNode(call.arguments().path("text").asText()), JSON.objectNode()))
                .build();
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = run(runtime(model, tools), List.of("use", "echo"), Optional.empty(), stdout, stderr);

        assertThat(exitCode).withFailMessage(stderr.toString()).isZero();
        assertThat(stdout.toString()).isEqualTo("tool round complete\n");
        assertThat(stderr.toString()).isEmpty();
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void reportsProviderFailureOnlyToStderr() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueueFailure(new IllegalStateException("provider unavailable"));
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = run(runtime(model, emptyTools()), List.of("fail"), Optional.empty(), stdout, stderr);

        assertThat(exitCode).isEqualTo(1);
        assertThat(stdout.toString()).isEmpty();
        assertThat(stderr.toString()).contains("provider unavailable");
    }

    @Test
    void reportsCancelledRequestOnlyToStderr() throws Exception {
        AbortController controller = new AbortController();
        controller.abort("cancelled by test");
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode = run(
                runtime(new FakeModelClient(), emptyTools()),
                List.of("cancel"),
                Optional.of(controller.signal()),
                stdout,
                stderr);

        assertThat(exitCode).isEqualTo(1);
        assertThat(stdout.toString()).isEmpty();
        assertThat(stderr.toString()).contains("cancelled by test");
    }

    private int run(
            CliRuntime runtime,
            List<String> messages,
            Optional<com.agent4j.core.runtime.AbortSignal> abortSignal,
            StringWriter stdout,
            StringWriter stderr
    ) {
        return new PrintModeRunner(temporaryDirectory).run(
                runtime,
                environment(),
                messages,
                abortSignal,
                new PrintWriter(stdout),
                new PrintWriter(stderr));
    }

    private CliRuntime runtime(FakeModelClient model, ToolRegistry tools) throws Exception {
        CliEnvironment environment = environment();
        Files.createDirectories(environment.cwd());
        ResourceDiscovery discovery = new ResourceLoader().discover(
                ResourceDiscoveryOptions.enabled(environment.homeDirectory(), environment.cwd()));
        return new CliRuntime(
                CodingAgentRuntime.builder()
                        .providerRegistry(com.agent4j.ai.AiProviderRegistry.fixedClient(
                                new com.agent4j.ai.AiModel(new AiModelReference("openai", "gpt-test"), "Test model"), model))
                        .toolRegistry(tools)
                        .clock(Clock.systemUTC())
                        .build(),
                discovery,
                new AiModelReference("openai", "gpt-test"));
    }

    private CliEnvironment environment() {
        return new CliEnvironment(temporaryDirectory.resolve("workspace"), temporaryDirectory.resolve("home"));
    }

    private boolean noTemporarySessions() throws Exception {
        try (var paths = Files.list(temporaryDirectory)) {
            return paths.noneMatch(path -> path.getFileName().toString().startsWith("agent4j-print-"));
        }
    }

    private static ToolRegistry emptyTools() {
        return InMemoryToolRegistry.builder().build();
    }

    private static List<AiStreamEvent> assistantText(String id, String text) {
        return List.of(new AiStreamEvent.MessageCompleted(
                id,
                new AiAssistantMessage(List.of(new AiTextContent(text)), AiStopReason.STOP, AiUsage.zero())));
    }
}
