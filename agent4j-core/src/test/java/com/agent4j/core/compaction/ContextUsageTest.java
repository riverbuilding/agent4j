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

class ContextUsageTest {
    @Test
    void calculatesSystemPromptAndTranscriptSeparately() {
        TokenEstimator estimator = text -> text == null ? 0 : text.length();

        ContextUsage usage = ContextUsage.calculate(
                "system",
                List.of(message("user-1", AgentMessageRole.USER, "hello")),
                estimator,
                OptionalLong.of(100));

        assertThat(usage.systemPromptTokens()).isEqualTo(6);
        assertThat(usage.messageTokens()).isEqualTo("user".length() + "hello".length());
        assertThat(usage.messageCount()).isEqualTo(1);
        assertThat(usage.totalTokens()).isEqualTo(15);
        assertThat(usage.remainingTokens()).hasValue(85);
    }

    @Test
    void treatsBlankSystemPromptAsAbsent() {
        ContextUsage usage = ContextUsage.calculate(
                " ",
                List.of(),
                new ApproximateTokenEstimator(),
                OptionalLong.empty());

        assertThat(usage.systemPromptTokens()).isZero();
        assertThat(usage.messageTokens()).isZero();
        assertThat(usage.remainingTokens()).isEmpty();
    }

    @Test
    void approximateEstimatorIsDeterministicAndAddsMessageOverhead() {
        ApproximateTokenEstimator estimator = new ApproximateTokenEstimator();

        assertThat(estimator.estimateText("abcd")).isEqualTo(1);
        assertThat(estimator.estimateText("abcde")).isEqualTo(2);
        assertThat(estimator.estimateText("")).isZero();
        assertThat(estimator.estimateMessage(message("user-1", AgentMessageRole.USER, "abcd")))
                .isEqualTo(6);
    }

    @Test
    void validatesNonNegativeValues() {
        assertThatThrownBy(() -> new ContextUsage(-1, 0, 0, OptionalLong.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token counts");
        assertThatThrownBy(() -> new ContextUsage(0, 0, -1, OptionalLong.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageCount");
        assertThatThrownBy(() -> new ContextUsage(0, 0, 0, OptionalLong.of(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contextWindowTokens");
    }

    @Test
    void detectsBudgetOverflowAtThreshold() {
        ContextUsage usage = new ContextUsage(3, 7, 2, OptionalLong.of(20));

        assertThat(usage.exceeds(10)).isTrue();
        assertThat(usage.exceeds(11)).isFalse();
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
