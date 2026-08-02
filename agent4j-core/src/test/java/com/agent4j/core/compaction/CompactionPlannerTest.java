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
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class CompactionPlannerTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final CompactionPlanner planner = new CompactionPlanner();

    @Test
    void manualCompactionUsesMessageTailWhenKeepTokensIsZero() {
        CompactionPlan plan = planner.plan(
                request(
                        CompactionReason.MANUAL,
                        CompactionConfig.builder()
                                .keepTokens(0)
                                .keepMessages(2)
                                .build(),
                        message("user-1", AgentMessageRole.USER, "one"),
                        message("assistant-1", AgentMessageRole.ASSISTANT, "two"),
                        message("user-2", AgentMessageRole.USER, "three"),
                        message("assistant-2", AgentMessageRole.ASSISTANT, "four")),
                new ApproximateTokenEstimator(),
                OptionalLong.empty());

        assertThat(plan.compact()).isTrue();
        assertThat(plan.cutoffIndex()).isEqualTo(2);
        assertThat(plan.prefixMessages()).extracting(AgentMessage::id)
                .containsExactly("user-1", "assistant-1");
        assertThat(plan.retainedMessages()).extracting(AgentMessage::id)
                .containsExactly("user-2", "assistant-2");
    }

    @Test
    void thresholdCompactionUsesTokenTailBudget() {
        TokenEstimator estimator = new TokenEstimator() {
            @Override
            public long estimateText(String text) {
                return 0;
            }

            @Override
            public long estimateMessage(AgentMessage message) {
                return Long.parseLong(message.metadata().path("tokens").asText());
            }
        };

        CompactionPlan plan = planner.plan(
                request(
                        CompactionReason.THRESHOLD,
                        CompactionConfig.builder()
                                .triggerMessages(1)
                                .keepTokens(10)
                                .build(),
                        tokenMessage("m1", 5),
                        tokenMessage("m2", 7),
                        tokenMessage("m3", 3)),
                estimator,
                OptionalLong.empty());

        assertThat(plan.compact()).isTrue();
        assertThat(plan.cutoffIndex()).isEqualTo(1);
        assertThat(plan.retainedMessages()).extracting(AgentMessage::id)
                .containsExactly("m2", "m3");
    }

    @Test
    void doesNotCompactBelowThreshold() {
        CompactionPlan plan = planner.plan(
                request(
                        CompactionReason.THRESHOLD,
                        CompactionConfig.builder()
                                .triggerMessages(10)
                                .triggerTokens(1_000)
                                .keepMessages(1)
                                .keepTokens(0)
                                .build(),
                        message("user-1", AgentMessageRole.USER, "hello")),
                new ApproximateTokenEstimator(),
                OptionalLong.empty());

        assertThat(plan.compact()).isFalse();
        assertThat(plan.prefixMessages()).isEmpty();
        assertThat(plan.retainedMessages()).isEmpty();
    }

    @Test
    void disabledConfigPreventsManualCompaction() {
        CompactionPlan plan = planner.plan(
                request(
                        CompactionReason.MANUAL,
                        CompactionConfig.builder().enabled(false).build(),
                        message("user-1", AgentMessageRole.USER, "hello"),
                        message("assistant-1", AgentMessageRole.ASSISTANT, "world")),
                new ApproximateTokenEstimator(),
                OptionalLong.empty());

        assertThat(plan.compact()).isFalse();
    }

    @Test
    void movesCutoffBeforeAssistantToolCallWhenTailStartsAtToolResult() {
        AgentMessage assistant = assistantToolCall("assistant-1", "tool-1", "read");
        AgentMessage toolResult = toolResult("tool-result-tool-1", "tool-1", "read", "README");

        CompactionPlan plan = planner.plan(
                request(
                        CompactionReason.MANUAL,
                        CompactionConfig.builder()
                                .keepTokens(0)
                                .keepMessages(2)
                                .build(),
                        message("user-1", AgentMessageRole.USER, "read file"),
                        assistant,
                        toolResult,
                        message("assistant-2", AgentMessageRole.ASSISTANT, "summary")),
                new ApproximateTokenEstimator(),
                OptionalLong.empty());

        assertThat(plan.compact()).isTrue();
        assertThat(plan.cutoffIndex()).isEqualTo(1);
        assertThat(plan.prefixMessages()).extracting(AgentMessage::id)
                .containsExactly("user-1");
        assertThat(plan.retainedMessages()).extracting(AgentMessage::id)
                .containsExactly("assistant-1", "tool-result-tool-1", "assistant-2");
    }

    @Test
    void movesCutoffBeforeAssistantWhenTailStartsAtSecondToolResult() {
        CompactionPlan plan = planner.plan(
                request(
                        CompactionReason.MANUAL,
                        CompactionConfig.builder()
                                .keepTokens(0)
                                .keepMessages(2)
                                .build(),
                        message("user-1", AgentMessageRole.USER, "run tools"),
                        assistantToolCall("assistant-1", "tool-1", "first", "tool-2", "second"),
                        toolResult("tool-result-tool-1", "tool-1", "first", "first output"),
                        toolResult("tool-result-tool-2", "tool-2", "second", "second output"),
                        message("assistant-2", AgentMessageRole.ASSISTANT, "done")),
                new ApproximateTokenEstimator(),
                OptionalLong.empty());

        assertThat(plan.compact()).isTrue();
        assertThat(plan.cutoffIndex()).isEqualTo(1);
        assertThat(plan.retainedMessages()).extracting(AgentMessage::id)
                .containsExactly("assistant-1", "tool-result-tool-1", "tool-result-tool-2", "assistant-2");
    }

    @Test
    void advancesPastOrphanToolResultsWhenAssistantCannotBeFound() {
        CompactionPlan plan = planner.plan(
                request(
                        CompactionReason.MANUAL,
                        CompactionConfig.builder()
                                .keepTokens(0)
                                .keepMessages(2)
                                .build(),
                        message("user-1", AgentMessageRole.USER, "old"),
                        message("assistant-1", AgentMessageRole.ASSISTANT, "old"),
                        toolResult("tool-result-tool-1", "missing", "read", "orphan"),
                        message("assistant-2", AgentMessageRole.ASSISTANT, "new")),
                new ApproximateTokenEstimator(),
                OptionalLong.empty());

        assertThat(plan.compact()).isTrue();
        assertThat(plan.cutoffIndex()).isEqualTo(3);
        assertThat(plan.retainedMessages()).extracting(AgentMessage::id)
                .containsExactly("assistant-2");
    }

    @Test
    void returnsNoOpWhenThereIsNoPrefixToSummarize() {
        CompactionPlan plan = planner.plan(
                request(
                        CompactionReason.MANUAL,
                        CompactionConfig.builder()
                                .keepTokens(0)
                                .keepMessages(10)
                                .build(),
                        message("user-1", AgentMessageRole.USER, "hello"),
                        message("assistant-1", AgentMessageRole.ASSISTANT, "world")),
                new ApproximateTokenEstimator(),
                OptionalLong.empty());

        assertThat(plan.compact()).isFalse();
    }

    @Test
    void resolvesDynamicTriggerAndKeepBudgetsFromContextWindow() {
        CompactionPlan plan = planner.plan(
                request(
                        CompactionReason.THRESHOLD,
                        CompactionConfig.defaults(),
                        message("user-1", AgentMessageRole.USER, "hello")),
                new ApproximateTokenEstimator(),
                OptionalLong.of(100_000));

        assertThat(plan.effectiveConfig().triggerTokens()).isEqualTo(80_000);
        assertThat(plan.effectiveConfig().keepTokens()).isEqualTo(8_000);
    }

    private static CompactionRequest request(
            CompactionReason reason,
            CompactionConfig config,
            AgentMessage... messages
    ) {
        return new CompactionRequest(
                "session-1",
                reason,
                List.of(messages),
                "system prompt",
                config,
                null);
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

    private static AgentMessage tokenMessage(String id, long tokens) {
        return new AgentMessage(
                id,
                null,
                Instant.parse("2026-07-28T10:00:00Z"),
                AgentMessageRole.USER,
                ContentBlocks.toJsonArray(List.of(new TextBlock(id, null))),
                JSON.objectNode().put("tokens", Long.toString(tokens)));
    }

    private static AgentMessage assistantToolCall(String id, String toolCallId, String toolName) {
        return assistantToolCall(id, toolCallId, toolName, null, null);
    }

    private static AgentMessage assistantToolCall(
            String id,
            String firstToolCallId,
            String firstToolName,
            String secondToolCallId,
            String secondToolName
    ) {
        List<ToolCallBlock> blocks = new java.util.ArrayList<>();
        blocks.add(new ToolCallBlock(new ToolCall(firstToolCallId, firstToolName, JSON.objectNode()), null));
        if (secondToolCallId != null) {
            blocks.add(new ToolCallBlock(new ToolCall(secondToolCallId, secondToolName, JSON.objectNode()), null));
        }
        return new AgentMessage(
                id,
                null,
                Instant.parse("2026-07-28T10:00:00Z"),
                AgentMessageRole.ASSISTANT,
                ContentBlocks.toJsonArray(blocks),
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
