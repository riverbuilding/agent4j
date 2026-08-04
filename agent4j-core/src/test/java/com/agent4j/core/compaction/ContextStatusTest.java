package com.agent4j.core.compaction;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiCost;
import com.agent4j.ai.AiInputType;
import com.agent4j.ai.AiModelCompat;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextStatusTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final AiModel MODEL = new AiModel(
            new AiModelReference("fake", "model"),
            "Fake",
            Optional.empty(),
            Optional.empty(),
            false,
            Map.of(),
            Set.of(),
            Set.of(AiInputType.TEXT),
            100,
            16,
            AiCost.zero(),
            AiModelCompat.defaults());

    @Test
    void reportsUsageEffectiveThresholdAndNoCompactionBelowThreshold() {
        ContextStatus status = service().status(
                request(
                        CompactionReason.THRESHOLD,
                        CompactionConfig.builder()
                                .triggerTokens(80)
                                .keepTokens(0)
                                .keepMessages(1)
                                .build(),
                        "system prompt",
                        message("user-1", AgentMessageRole.USER, "hello")),
                MODEL);

        assertThat(status.compactionNeeded()).isFalse();
        assertThat(status.cutoffIndex()).isZero();
        assertThat(status.totalTokens()).isEqualTo("system prompt".length() + "user".length() + "hello".length());
        assertThat(status.remainingTokens()).hasValue(78);
        assertThat(status.contextWindowUsageRatio()).isPresent();
        assertThat(status.contextWindowUsageRatio().getAsDouble()).isEqualTo(0.22);
        assertThat(status.triggerTokens()).isEqualTo(80);
        assertThat(status.triggerMessages()).isEqualTo(50);
    }

    @Test
    void reportsCompactionNeededAndCutoffWhenThresholdPlanWouldCompact() {
        ContextStatus status = service().status(
                request(
                        CompactionReason.THRESHOLD,
                        CompactionConfig.builder()
                                .triggerMessages(2)
                                .keepTokens(0)
                                .keepMessages(1)
                                .build(),
                        null,
                        message("user-1", AgentMessageRole.USER, "one"),
                        message("assistant-1", AgentMessageRole.ASSISTANT, "two"),
                        message("user-2", AgentMessageRole.USER, "three")),
                MODEL);

        assertThat(status.compactionNeeded()).isTrue();
        assertThat(status.cutoffIndex()).isEqualTo(2);
        assertThat(status.usage().messageCount()).isEqualTo(3);
        assertThat(status.reason()).isEqualTo(CompactionReason.THRESHOLD);
    }

    @Test
    void createsStatusFromPlanWithoutContextWindowRatio() {
        ContextUsage usage = new ContextUsage(1, 2, 1, OptionalLong.empty());
        ContextStatus status = ContextStatus.fromPlan(CompactionPlan.noOp(
                CompactionReason.MANUAL,
                usage,
                CompactionConfig.defaults()));

        assertThat(status.contextWindowUsageRatio()).isEmpty();
        assertThat(status.remainingTokens()).isEmpty();
        assertThat(status.compactionNeeded()).isFalse();
    }

    @Test
    void validatesCutoffAndRatio() {
        ContextUsage usage = new ContextUsage(0, 0, 0, OptionalLong.empty());

        assertThatThrownBy(() -> new ContextStatus(
                usage,
                CompactionConfig.defaults(),
                CompactionReason.MANUAL,
                false,
                -1,
                OptionalDouble.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cutoffIndex");
    }

    private static CompactionService service() {
        return new CompactionService(
                new CompactionPlanner(),
                new CompactionSerializer(),
                text -> text == null ? 0 : text.length(),
                Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), java.time.ZoneOffset.UTC));
    }

    private static CompactionRequest request(
            CompactionReason reason,
            CompactionConfig config,
            String systemPrompt,
            AgentMessage... messages
    ) {
        return new CompactionRequest(
                "session-1",
                reason,
                List.of(messages),
                systemPrompt,
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
}
