package com.agent4j.core.compaction;

import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompactionRequestResultTest {
    @Test
    void requestNormalizesOptionalFieldsAndCopiesMessages() {
        List<AgentMessage> messages = new java.util.ArrayList<>();
        messages.add(message("user-1", AgentMessageRole.USER, "hello"));

        CompactionRequest request = new CompactionRequest(
                "session-1",
                CompactionReason.MANUAL,
                messages,
                " ",
                CompactionConfig.defaults(),
                " focus auth ");
        messages.clear();

        assertThat(request.messages()).hasSize(1);
        assertThat(request.optionalSystemPrompt()).isEmpty();
        assertThat(request.optionalFocusInstructions()).contains(" focus auth ");
    }

    @Test
    void compactedResultRequiresSummaryAndBuildsCompactedMessages() {
        ContextUsage before = new ContextUsage(1, 20, 3, OptionalLong.of(100));
        ContextUsage after = new ContextUsage(1, 8, 2, OptionalLong.of(100));
        AgentMessage summary = message("summary-1", AgentMessageRole.COMPACTION_SUMMARY, "summary");
        AgentMessage tail = message("assistant-1", AgentMessageRole.ASSISTANT, "tail");

        CompactionResult result = new CompactionResult(
                CompactionReason.THRESHOLD,
                summary,
                List.of(tail),
                before,
                after);

        assertThat(result.compacted()).isTrue();
        assertThat(result.optionalSummaryMessage()).contains(summary);
        assertThat(result.compactedMessages()).containsExactly(summary, tail);
    }

    @Test
    void noOpResultCarriesUsageWithoutSummary() {
        ContextUsage usage = new ContextUsage(1, 2, 1, OptionalLong.empty());

        CompactionResult result = CompactionResult.noOp(CompactionReason.THRESHOLD, usage);

        assertThat(result.compacted()).isFalse();
        assertThat(result.optionalSummaryMessage()).isEmpty();
        assertThat(result.usageBefore()).isEqualTo(usage);
        assertThat(result.usageAfter()).isEqualTo(usage);
    }

    @Test
    void rejectsCompactedResultWithoutSummary() {
        ContextUsage usage = new ContextUsage(0, 0, 0, OptionalLong.empty());

        assertThatThrownBy(() -> new CompactionResult(
                        true,
                        CompactionReason.MANUAL,
                        null,
                        List.of(),
                        usage,
                        usage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("summaryMessage");
    }

    private static AgentMessage message(String id, AgentMessageRole role, String text) {
        return new AgentMessage(
                id,
                null,
                Instant.parse("2026-07-28T10:00:00Z"),
                role,
                ContentBlocks.toJsonArray(List.of(new TextBlock(text, null))),
                JsonNodeFactory.instance.objectNode());
    }
}
