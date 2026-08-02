package com.agent4j.core.compaction;

import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolCallBlock;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompactionSerializerTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final CompactionSerializer serializer = new CompactionSerializer();

    @Test
    void serializesMixedTextToolCallAndToolResultTranscript() {
        String rendered = serializer.serialize(List.of(
                message("user-1", AgentMessageRole.USER, "Read README.md"),
                assistantToolCall("assistant-1", "tool-1", "read"),
                toolResult("tool-result-tool-1", "tool-1", "read", "README contents"),
                message("assistant-2", AgentMessageRole.ASSISTANT, "The README says hello.")));

        assertThat(rendered).isEqualTo("""
                Human: Read README.md

                AI: [tool_call: read id=tool-1 args={"path":"README.md"}]

                Tool: [tool_result: read id=tool-1] README contents

                AI: The README says hello.""");
    }

    @Test
    void excludesSystemPromptFromSerializedConversation() {
        String rendered = serializer.serialize(List.of(
                message("system-1", AgentMessageRole.SYSTEM, "Follow rules."),
                message("user-1", AgentMessageRole.USER, "hello")));

        assertThat(rendered).isEqualTo("Human: hello");
    }

    @Test
    void includesPriorCompactionSummaryAsSummaryInput() {
        String rendered = serializer.serialize(List.of(
                message("summary-1", AgentMessageRole.COMPACTION_SUMMARY, "old summary"),
                message("user-2", AgentMessageRole.USER, "continue")));

        assertThat(rendered).isEqualTo("""
                Compaction Summary: old summary

                Human: continue""");
    }

    @Test
    void boundsLargeToolResultOutput() {
        CompactionSerializer bounded = new CompactionSerializer(8);

        String rendered = bounded.serialize(List.of(
                toolResult("tool-result-tool-1", "tool-1", "read", "0123456789abcdef")));

        assertThat(rendered).isEqualTo("Tool: [tool_result: read id=tool-1] 01234567...");
    }

    @Test
    void rendersNonTextToolResultContentAsJson() {
        AgentMessage result = new AgentMessage(
                "tool-result-tool-1",
                null,
                Instant.parse("2026-07-28T10:00:00Z"),
                AgentMessageRole.TOOL_RESULT,
                JSON.objectNode().put("status", "ok"),
                JSON.objectNode()
                        .put("toolCallId", "tool-1")
                        .put("toolName", "status")
                        .put("error", false));

        assertThat(serializer.serialize(List.of(result)))
                .isEqualTo("Tool: [tool_result: status id=tool-1] {\"status\":\"ok\"}");
    }

    @Test
    void buildsPromptFromConfigTemplateAndFocusInstructions() {
        CompactionRequest request = new CompactionRequest(
                "session-1",
                CompactionReason.MANUAL,
                List.of(message("user-1", AgentMessageRole.USER, "hello")),
                "system",
                CompactionConfig.builder()
                        .summaryPrompt("Summarize:\n{messages}")
                        .build(),
                "preserve auth decisions");

        String prompt = serializer.buildSummaryPrompt(request, request.messages());

        assertThat(prompt).isEqualTo("""
                Summarize:
                Human: hello

                <focusInstructions>
                preserve auth decisions
                </focusInstructions>""");
    }

    @Test
    void omitsFocusInstructionsWhenAbsent() {
        CompactionRequest request = new CompactionRequest(
                "session-1",
                CompactionReason.THRESHOLD,
                List.of(message("user-1", AgentMessageRole.USER, "hello")),
                null,
                CompactionConfig.builder()
                        .summaryPrompt("Summarize:\n{messages}")
                        .build(),
                null);

        assertThat(serializer.buildSummaryPrompt(request, request.messages()))
                .isEqualTo("""
                        Summarize:
                        Human: hello""");
    }

    private static AgentMessage message(String id, AgentMessageRole role, String text) {
        return new AgentMessage(
                id,
                null,
                Instant.parse("2026-07-28T10:00:00Z"),
                role,
                ContentBlocks.toJsonArray(List.of(new TextBlock(text, null))),
                JSON.objectNode());
    }

    private static AgentMessage assistantToolCall(String id, String toolCallId, String toolName) {
        return new AgentMessage(
                id,
                null,
                Instant.parse("2026-07-28T10:00:00Z"),
                AgentMessageRole.ASSISTANT,
                ContentBlocks.toJsonArray(List.of(new ToolCallBlock(
                        new ToolCall(toolCallId, toolName, JSON.objectNode().put("path", "README.md")),
                        null))),
                JSON.objectNode());
    }

    private static AgentMessage toolResult(String id, String toolCallId, String toolName, String text) {
        return new AgentMessage(
                id,
                null,
                Instant.parse("2026-07-28T10:00:00Z"),
                AgentMessageRole.TOOL_RESULT,
                ContentBlocks.toJsonArray(List.of(new TextBlock(text, null))),
                JSON.objectNode()
                        .put("toolCallId", toolCallId)
                        .put("toolName", toolName)
                        .put("error", false));
    }
}
