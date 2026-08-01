package com.agent4j.core.compaction;

import java.util.Objects;
import java.util.Optional;

public record CompactionConfig(
        boolean enabled,
        long triggerTokens,
        int triggerMessages,
        long reservedTokens,
        int keepMessages,
        long keepTokens,
        long keepTokensMin,
        long keepTokensMax,
        double keepTokensRatio,
        String summaryPrompt,
        boolean overflowRetryEnabled
) {
    public static final long FALLBACK_TRIGGER_TOKENS = 160_000;
    public static final long DYNAMIC_TRIGGER_TOKENS = 0;
    public static final long MESSAGE_BASED_KEEP_TOKENS = 0;
    public static final long DYNAMIC_KEEP_TOKENS = -1;

    public static final String DEFAULT_SUMMARY_PROMPT = """
            <role>
            Context Extraction Assistant
            </role>

            <primary_objective>
            Your sole objective in this task is to extract the highest quality/most relevant context from the conversation history below.
            </primary_objective>

            <instructions>
            The conversation history below will be replaced with the context you extract in this step. Keep only the most important information needed to continue the session.

            Structure your summary using these sections:

            ## SESSION INTENT
            ## SUMMARY
            ## ARTIFACTS
            ## NEXT STEPS
            </instructions>

            <messages>
            {messages}
            </messages>
            """;

    public CompactionConfig {
        if (triggerTokens < 0) {
            throw new IllegalArgumentException("triggerTokens must be non-negative");
        }
        if (triggerMessages < 0) {
            throw new IllegalArgumentException("triggerMessages must be non-negative");
        }
        if (reservedTokens < 0 || keepTokensMin < 0 || keepTokensMax < 0) {
            throw new IllegalArgumentException("token budgets must be non-negative");
        }
        if (keepMessages < 0) {
            throw new IllegalArgumentException("keepMessages must be non-negative");
        }
        if (keepTokens < DYNAMIC_KEEP_TOKENS) {
            throw new IllegalArgumentException("keepTokens must be -1, 0, or positive");
        }
        if (keepTokensMax < keepTokensMin) {
            throw new IllegalArgumentException("keepTokensMax must be greater than or equal to keepTokensMin");
        }
        if (keepTokensRatio < 0 || Double.isNaN(keepTokensRatio) || Double.isInfinite(keepTokensRatio)) {
            throw new IllegalArgumentException("keepTokensRatio must be a finite non-negative value");
        }
        Objects.requireNonNull(summaryPrompt, "summaryPrompt");
        if (!summaryPrompt.contains("{messages}")) {
            throw new IllegalArgumentException("summaryPrompt must contain {messages}");
        }
    }

    public static CompactionConfig defaults() {
        return builder().build();
    }

    public Optional<Long> fixedKeepTokens() {
        return keepTokens > 0 ? Optional.of(keepTokens) : Optional.empty();
    }

    public boolean usesDynamicTrigger() {
        return triggerTokens == DYNAMIC_TRIGGER_TOKENS;
    }

    public boolean usesDynamicKeepTokens() {
        return keepTokens == DYNAMIC_KEEP_TOKENS;
    }

    public boolean usesMessageBasedKeep() {
        return keepTokens == MESSAGE_BASED_KEEP_TOKENS;
    }

    public CompactionConfig withEffectiveBudgets(long effectiveTriggerTokens, long effectiveKeepTokens) {
        return new CompactionConfig(
                enabled,
                effectiveTriggerTokens,
                triggerMessages,
                reservedTokens,
                keepMessages,
                effectiveKeepTokens,
                keepTokensMin,
                keepTokensMax,
                keepTokensRatio,
                summaryPrompt,
                overflowRetryEnabled);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean enabled = true;
        private long triggerTokens = DYNAMIC_TRIGGER_TOKENS;
        private int triggerMessages = 50;
        private long reservedTokens = 20_000;
        private int keepMessages = 20;
        private long keepTokens = DYNAMIC_KEEP_TOKENS;
        private long keepTokensMin = 2_000;
        private long keepTokensMax = 8_000;
        private double keepTokensRatio = 0.25;
        private String summaryPrompt = DEFAULT_SUMMARY_PROMPT;
        private boolean overflowRetryEnabled = true;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder triggerTokens(long triggerTokens) {
            this.triggerTokens = triggerTokens;
            return this;
        }

        public Builder triggerMessages(int triggerMessages) {
            this.triggerMessages = triggerMessages;
            return this;
        }

        public Builder reservedTokens(long reservedTokens) {
            this.reservedTokens = reservedTokens;
            return this;
        }

        public Builder keepMessages(int keepMessages) {
            this.keepMessages = keepMessages;
            return this;
        }

        public Builder keepTokens(long keepTokens) {
            this.keepTokens = keepTokens;
            return this;
        }

        public Builder keepTokensMin(long keepTokensMin) {
            this.keepTokensMin = keepTokensMin;
            return this;
        }

        public Builder keepTokensMax(long keepTokensMax) {
            this.keepTokensMax = keepTokensMax;
            return this;
        }

        public Builder keepTokensRatio(double keepTokensRatio) {
            this.keepTokensRatio = keepTokensRatio;
            return this;
        }

        public Builder summaryPrompt(String summaryPrompt) {
            this.summaryPrompt = summaryPrompt;
            return this;
        }

        public Builder overflowRetryEnabled(boolean overflowRetryEnabled) {
            this.overflowRetryEnabled = overflowRetryEnabled;
            return this;
        }

        public CompactionConfig build() {
            return new CompactionConfig(
                    enabled,
                    triggerTokens,
                    triggerMessages,
                    reservedTokens,
                    keepMessages,
                    keepTokens,
                    keepTokensMin,
                    keepTokensMax,
                    keepTokensRatio,
                    summaryPrompt,
                    overflowRetryEnabled);
        }
    }
}
