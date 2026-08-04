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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompactionMessagePreprocessorTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final CompactionMessagePreprocessor preprocessor = new CompactionMessagePreprocessor(new CharacterEstimator());

    @Test
    void prunesOlderLargeToolResultsToHeadTailPreview() {
        AgentMessage first = toolResult("result-1", "tool-1", "execute", "0123456789abcdef");
        AgentMessage excluded = toolResult("result-2", "tool-2", "read_file", "0123456789abcdef");
        AgentMessage recent = toolResult("result-3", "tool-3", "execute", "abcdefghij012345ABCDEFGHIJ");

        List<AgentMessage> pruned = preprocessor.pruneToolResults(
                List.of(first, excluded, recent),
                CompactionConfig.PruneConfig.builder()
                        .protectTokens(20)
                        .minimumTokens(0)
                        .maxOutputChars(8)
                        .excludedTools(Set.of("read_file"))
                        .build());

        assertThat(pruned.get(0).textContent()).isEqualTo("""
                0123

                ...(8 chars pruned)...

                cdef""");
        assertThat(pruned.get(1).textContent()).isEqualTo("0123456789abcdef");
        assertThat(pruned.get(2).textContent()).isEqualTo("abcdefghij012345ABCDEFGHIJ");
    }

    @Test
    void truncatesStringToolCallArgumentsOnlyBeforeKeepWindow() {
        AgentMessage oldAssistant = assistantToolCall("assistant-1", "tool-1", "write_file", "abcdefghijklmnopqrstuvwxyz");
        AgentMessage recentAssistant = assistantToolCall("assistant-2", "tool-2", "write_file", "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        AgentMessage prompt = message("user-1", AgentMessageRole.USER, "continue");

        List<AgentMessage> truncated = preprocessor.truncateArgs(
                List.of(oldAssistant, recentAssistant, prompt),
                CompactionConfig.TruncateArgsConfig.builder()
                        .triggerMessages(1)
                        .keepMessages(2)
                        .maxArgLength(10)
                        .truncationText("...(argument truncated)")
                        .build());

        assertThat(truncated.get(0).content().get(0).path("arguments").path("content").asText())
                .isEqualTo("abcdefghijklmnopqrst...(argument truncated)");
        assertThat(truncated.get(1).content().get(0).path("arguments").path("content").asText())
                .isEqualTo("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        assertThat(truncated.get(2)).isSameAs(prompt);
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

    private static AgentMessage assistantToolCall(String id, String toolCallId, String toolName, String content) {
        return new AgentMessage(
                id,
                null,
                Instant.parse("2026-07-28T10:00:00Z"),
                AgentMessageRole.ASSISTANT,
                ContentBlocks.toJsonArray(List.of(new ToolCallBlock(
                        new ToolCall(toolCallId, toolName, JSON.objectNode()
                                .put("path", "file.txt")
                                .put("content", content)),
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

    private static final class CharacterEstimator implements TokenEstimator {
        @Override
        public long estimateText(String text) {
            return text == null ? 0 : text.length();
        }

        @Override
        public long estimateMessage(AgentMessage message) {
            return message.textContent().length();
        }
    }
}
