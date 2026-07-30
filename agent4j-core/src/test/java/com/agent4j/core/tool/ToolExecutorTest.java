package com.agent4j.core.tool;

import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.runtime.AbortController;
import com.agent4j.core.runtime.AgentAbortException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolExecutorTest {
    private static final JsonNode INPUT_SCHEMA = JsonNodeFactory.instance.objectNode()
            .put("type", "object");

    @Test
    void executesRegisteredTool() {
        ToolSpec spec = new ToolSpec("echo", "Echo input", INPUT_SCHEMA);
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(spec, (call, context) -> new ToolResult(
                        call.id(),
                        call.name(),
                        false,
                        call.arguments().get("text"),
                        JsonNodeFactory.instance.objectNode().put("cwd", context.cwd().toString())))
                .build();
        ToolExecutor executor = new ToolExecutor(registry);

        ToolResult result = executor.execute(
                new ToolCall("call-1", "echo", JsonNodeFactory.instance.objectNode().put("text", "hello")),
                context());

        assertThat(result.error()).isFalse();
        assertThat(result.content().asText()).isEqualTo("hello");
        assertThat(result.metadata().get("cwd").asText()).isEqualTo("/repo");
    }

    @Test
    void returnsStableErrorResultForUnknownTool() {
        ToolExecutor executor = new ToolExecutor(InMemoryToolRegistry.builder().build());

        ToolResult result = executor.execute(
                new ToolCall("call-1", "missing", JsonNodeFactory.instance.objectNode()),
                context());

        assertThat(result.error()).isTrue();
        assertThat(result.toolCallId()).isEqualTo("call-1");
        assertThat(result.toolName()).isEqualTo("missing");
        assertThat(result.content().asText()).isEqualTo("unknown tool: missing");
        assertThat(result.metadata().get("message").asText()).isEqualTo("unknown tool: missing");
    }

    @Test
    void convertsToolExceptionToErrorResult() {
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("fail", "Fails", INPUT_SCHEMA), (call, context) -> {
                    throw new IllegalStateException("nope");
                })
                .build();
        ToolExecutor executor = new ToolExecutor(registry);

        ToolResult result = executor.execute(new ToolCall("call-1", "fail", JsonNodeFactory.instance.objectNode()), context());

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo(TextNode.valueOf("nope"));
        assertThat(result.metadata().get("exceptionClass").asText()).isEqualTo(IllegalStateException.class.getName());
    }

    @Test
    void rejectsDuplicateToolNames() {
        ToolSpec spec = new ToolSpec("echo", "Echo input", INPUT_SCHEMA);
        InMemoryToolRegistry.Builder builder = InMemoryToolRegistry.builder()
                .register(spec, (call, context) -> new ToolResult(call.id(), call.name(), false, call.arguments(), null));

        assertThatThrownBy(() -> builder.register(spec, (call, context) -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void preservesRegistrationOrderForSpecs() {
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("first", "First", INPUT_SCHEMA), (call, context) -> null)
                .register(new ToolSpec("second", "Second", INPUT_SCHEMA), (call, context) -> null)
                .build();

        assertThat(registry.specs()).extracting(ToolSpec::name).containsExactly("first", "second");
    }

    @Test
    void abortsBeforeExecutingTool() {
        AbortController abortController = new AbortController();
        abortController.abort("stop");
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("echo", "Echo input", INPUT_SCHEMA), (call, context) -> {
                    throw new AssertionError("tool should not execute");
                })
                .build();
        ToolContext context = new ToolContext("session-1", Path.of("/repo"), Clock.systemUTC(), abortController.signal(), Map.of());

        assertThatThrownBy(() -> new ToolExecutor(registry).execute(
                new ToolCall("call-1", "echo", JsonNodeFactory.instance.objectNode()),
                context))
                .isInstanceOf(AgentAbortException.class)
                .hasMessage("stop");
    }

    @Test
    void toolContextPublishesUpdatesThroughSink() {
        List<JsonNode> updates = new ArrayList<>();
        ToolContext context = new ToolContext(
                "session-1",
                Path.of("/repo"),
                Clock.systemUTC(),
                new AbortController().signal(),
                Map.of(),
                updates::add);

        context.publishUpdate(JsonNodeFactory.instance.objectNode().put("status", "running"));

        assertThat(updates).hasSize(1);
        assertThat(updates.getFirst().path("status").asText()).isEqualTo("running");
    }

    @Test
    void createsStableBlockedToolResult() {
        ToolResult result = ToolResult.blocked(
                new ToolCall("call-1", "write", JsonNodeFactory.instance.objectNode()),
                "blocked by policy");

        assertThat(result.toolCallId()).isEqualTo("call-1");
        assertThat(result.toolName()).isEqualTo("write");
        assertThat(result.error()).isTrue();
        assertThat(result.content().asText()).isEqualTo("blocked by policy");
        assertThat(result.metadata().path("message").asText()).isEqualTo("blocked by policy");
        assertThat(result.metadata().path("blocked").asBoolean()).isTrue();
    }

    private static ToolContext context() {
        return new ToolContext(
                "session-1",
                Path.of("/repo"),
                Clock.systemUTC(),
                new AbortController().signal(),
                Map.of("key", "value"));
    }
}
