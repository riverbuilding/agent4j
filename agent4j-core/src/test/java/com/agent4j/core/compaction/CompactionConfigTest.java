package com.agent4j.core.compaction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompactionConfigTest {
    @Test
    void defaultsMirrorPiDynamicCompactionShape() {
        CompactionConfig config = CompactionConfig.defaults();

        assertThat(config.enabled()).isTrue();
        assertThat(config.triggerMessages()).isEqualTo(50);
        assertThat(config.triggerTokens()).isEqualTo(CompactionConfig.DYNAMIC_TRIGGER_TOKENS);
        assertThat(config.reservedTokens()).isEqualTo(20_000);
        assertThat(config.keepMessages()).isEqualTo(20);
        assertThat(config.keepTokens()).isEqualTo(CompactionConfig.DYNAMIC_KEEP_TOKENS);
        assertThat(config.keepTokensMin()).isEqualTo(2_000);
        assertThat(config.keepTokensMax()).isEqualTo(8_000);
        assertThat(config.keepTokensRatio()).isEqualTo(0.25);
        assertThat(config.overflowRetryEnabled()).isTrue();
        assertThat(config.summaryPrompt()).contains("{messages}");
        assertThat(config.truncateArgsConfig()).isNull();
        assertThat(config.pruneConfig()).isNotNull();
        assertThat(config.pruneConfig().protectTokens()).isEqualTo(40_000);
        assertThat(config.pruneConfig().minimumTokens()).isEqualTo(20_000);
        assertThat(config.pruneConfig().maxOutputChars()).isEqualTo(2_000);
        assertThat(config.pruneConfig().excludedTools())
                .containsExactlyInAnyOrder("read_file", "memory_search", "memory_get", "session_search");
    }

    @Test
    void exposesBudgetModes() {
        assertThat(CompactionConfig.defaults().usesDynamicTrigger()).isTrue();
        assertThat(CompactionConfig.defaults().usesDynamicKeepTokens()).isTrue();

        CompactionConfig messageBased = CompactionConfig.builder().keepTokens(0).build();
        assertThat(messageBased.usesMessageBasedKeep()).isTrue();
        assertThat(messageBased.fixedKeepTokens()).isEmpty();

        CompactionConfig fixed = CompactionConfig.builder().keepTokens(4096).build();
        assertThat(fixed.fixedKeepTokens()).contains(4096L);
    }

    @Test
    void rejectsInvalidBudgetsAndSummaryPrompt() {
        assertThatThrownBy(() -> CompactionConfig.builder().triggerTokens(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("triggerTokens");
        assertThatThrownBy(() -> CompactionConfig.builder().keepTokens(-2).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keepTokens");
        assertThatThrownBy(() -> CompactionConfig.builder().keepTokensMin(10).keepTokensMax(5).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keepTokensMax");
        assertThatThrownBy(() -> CompactionConfig.builder().summaryPrompt("summarize").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{messages}");
    }

    @Test
    void createsResolvedCopyWithEffectiveBudgets() {
        CompactionConfig config = CompactionConfig.defaults()
                .withEffectiveBudgets(100_000, 6_000);

        assertThat(config.triggerTokens()).isEqualTo(100_000);
        assertThat(config.keepTokens()).isEqualTo(6_000);
        assertThat(config.keepTokensMin()).isEqualTo(2_000);
        assertThat(config.keepTokensMax()).isEqualTo(8_000);
    }
}
