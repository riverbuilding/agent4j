package com.agent4j.core.compaction;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
        boolean overflowRetryEnabled,
        TruncateArgsConfig truncateArgsConfig,
        PruneConfig pruneConfig
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
                overflowRetryEnabled,
                truncateArgsConfig,
                pruneConfig);
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
        private TruncateArgsConfig truncateArgsConfig = null;
        private PruneConfig pruneConfig = PruneConfig.defaults();

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

        public Builder truncateArgs(TruncateArgsConfig truncateArgsConfig) {
            this.truncateArgsConfig = truncateArgsConfig;
            return this;
        }

        public Builder prune(PruneConfig pruneConfig) {
            this.pruneConfig = pruneConfig;
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
                    overflowRetryEnabled,
                    truncateArgsConfig,
                    pruneConfig);
        }
    }

    public record TruncateArgsConfig(
            int triggerMessages,
            long triggerTokens,
            int keepMessages,
            long keepTokens,
            int maxArgLength,
            String truncationText
    ) {
        public TruncateArgsConfig {
            if (triggerMessages < 0 || triggerTokens < 0 || keepMessages < 0 || keepTokens < 0) {
                throw new IllegalArgumentException("truncate argument thresholds must be non-negative");
            }
            if (maxArgLength < 0) {
                throw new IllegalArgumentException("maxArgLength must be non-negative");
            }
            Objects.requireNonNull(truncationText, "truncationText");
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private int triggerMessages = 25;
            private long triggerTokens = 40_000;
            private int keepMessages = 20;
            private long keepTokens = 0;
            private int maxArgLength = 2_000;
            private String truncationText = "...(argument truncated)";

            public Builder triggerMessages(int triggerMessages) {
                this.triggerMessages = triggerMessages;
                return this;
            }

            public Builder triggerTokens(long triggerTokens) {
                this.triggerTokens = triggerTokens;
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

            public Builder maxArgLength(int maxArgLength) {
                this.maxArgLength = maxArgLength;
                return this;
            }

            public Builder truncationText(String truncationText) {
                this.truncationText = truncationText;
                return this;
            }

            public TruncateArgsConfig build() {
                return new TruncateArgsConfig(
                        triggerMessages,
                        triggerTokens,
                        keepMessages,
                        keepTokens,
                        maxArgLength,
                        truncationText);
            }
        }
    }

    public record PruneConfig(
            long protectTokens,
            long minimumTokens,
            int maxOutputChars,
            Set<String> excludedTools
    ) {
        public PruneConfig {
            if (protectTokens < 0 || minimumTokens < 0) {
                throw new IllegalArgumentException("prune token budgets must be non-negative");
            }
            if (maxOutputChars < 0) {
                throw new IllegalArgumentException("maxOutputChars must be non-negative");
            }
            excludedTools = excludedTools == null ? Set.of() : Set.copyOf(excludedTools);
        }

        public static PruneConfig defaults() {
            return builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private long protectTokens = 40_000;
            private long minimumTokens = 20_000;
            private int maxOutputChars = 2_000;
            private Set<String> excludedTools = Set.of(
                    "read_file",
                    "memory_search",
                    "memory_get",
                    "session_search");

            public Builder protectTokens(long protectTokens) {
                this.protectTokens = protectTokens;
                return this;
            }

            public Builder minimumTokens(long minimumTokens) {
                this.minimumTokens = minimumTokens;
                return this;
            }

            public Builder maxOutputChars(int maxOutputChars) {
                this.maxOutputChars = maxOutputChars;
                return this;
            }

            public Builder excludedTools(Set<String> excludedTools) {
                this.excludedTools = excludedTools;
                return this;
            }

            public PruneConfig build() {
                return new PruneConfig(protectTokens, minimumTokens, maxOutputChars, excludedTools);
            }
        }
    }
}
