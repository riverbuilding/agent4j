package com.agent4j.cli;

import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveEventRendererTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Test
    void rendersStreamingTextOnceAndWritesStatusAndFailureEventsToExpectedStreams() {
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        InteractiveEventRenderer renderer = new InteractiveEventRenderer(
                new InteractiveTerminal(new StringReader(""), new PrintWriter(stdout), new PrintWriter(stderr)));
        ToolCall call = new ToolCall("tool-1", "read", JSON.objectNode().put("path", "README.md"));

        renderer.render(new AgentEvent.MessageUpdated("session", NOW, "assistant-1", JSON.objectNode()
                .put("type", "text_delta").put("contentIndex", 0).put("delta", "hello")));
        renderer.render(new AgentEvent.MessageEnded("session", NOW, assistant("assistant-1", "hello")));
        renderer.render(new AgentEvent.ToolExecutionStarted("session", NOW, call));
        renderer.render(new AgentEvent.ToolExecutionUpdated("session", NOW, call.id(), JSON.textNode("reading")));
        renderer.render(new AgentEvent.ToolExecutionEnded("session", NOW,
                new ToolResult(call.id(), call.name(), false, JSON.textNode("done"), JSON.objectNode())));
        renderer.render(new AgentEvent.RetryStarted("session", NOW, 2, "temporary failure"));
        renderer.render(new AgentEvent.RetryCompleted("session", NOW, 2, true));
        renderer.render(new AgentEvent.CompactionStarted("session", NOW, "threshold"));
        renderer.render(new AgentEvent.CompactionCompleted("session", NOW, "summary-1"));
        renderer.render(new AgentEvent.MessageUpdated("session", NOW, "assistant-2", JSON.objectNode()
                .put("type", "message_error").put("error", "stream failed")));
        renderer.render(new AgentEvent.AgentAborted("session", NOW, "cancelled"));

        assertThat(stdout.toString()).isEqualTo("hello\n"
                + "[tool read started]\n"
                + "[tool tool-1 update: reading]\n"
                + "[tool read completed: done]\n"
                + "[retry 2: temporary failure]\n"
                + "[retry 2 completed]\n"
                + "[compacting: threshold]\n"
                + "[compaction completed: summary-1]\n");
        assertThat(stderr.toString()).isEqualTo("Error: stream failed\nError: aborted: cancelled\n");
    }

    @Test
    void fallsBackToCompletedAssistantTextWhenProviderDidNotStreamDeltas() {
        StringWriter stdout = new StringWriter();
        InteractiveEventRenderer renderer = new InteractiveEventRenderer(
                new InteractiveTerminal(new StringReader(""), new PrintWriter(stdout), new PrintWriter(new StringWriter())));

        renderer.render(new AgentEvent.MessageEnded("session", NOW, assistant("assistant-1", "final only")));

        assertThat(stdout.toString()).isEqualTo("final only\n");
    }

    @Test
    void usesJLineStylesForMarkdownStatusAndErrorsWhenAnsiIsEnabled() {
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        InteractiveEventRenderer renderer = new InteractiveEventRenderer(
                new InteractiveTerminal(new StringReader(""), new PrintWriter(stdout), new PrintWriter(stderr), true));

        renderer.render(new AgentEvent.MessageEnded("session", NOW,
                assistant("assistant-1", "# Heading\n\n`code`")));
        renderer.render(new AgentEvent.RetryStarted("session", NOW, 1, "temporary"));
        renderer.render(new AgentEvent.AgentAborted("session", NOW, "cancelled"));

        assertThat(stdout.toString()).contains("Heading", "code", "\u001B[");
        assertThat(stderr.toString()).contains("Error: aborted: cancelled", "\u001B[");
    }

    private static AgentMessage assistant(String id, String text) {
        return AgentMessage.assistantText(id, null, NOW, text, JSON.objectNode());
    }
}
