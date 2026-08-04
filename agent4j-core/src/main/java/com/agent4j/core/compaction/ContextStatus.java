package com.agent4j.core.compaction;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalLong;

public record ContextStatus(
        ContextUsage usage,
        CompactionConfig effectiveConfig,
        CompactionReason reason,
        boolean compactionNeeded,
        int cutoffIndex,
        OptionalDouble contextWindowUsageRatio
) {
    public ContextStatus {
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(effectiveConfig, "effectiveConfig");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(contextWindowUsageRatio, "contextWindowUsageRatio");
        if (cutoffIndex < 0) {
            throw new IllegalArgumentException("cutoffIndex must be non-negative");
        }
    }

    public static ContextStatus fromPlan(CompactionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new ContextStatus(
                plan.usage(),
                plan.effectiveConfig(),
                plan.reason(),
                plan.compact(),
                plan.cutoffIndex(),
                usageRatio(plan.usage()));
    }

    public long totalTokens() {
        return usage.totalTokens();
    }

    public OptionalLong remainingTokens() {
        return usage.remainingTokens();
    }

    public long triggerTokens() {
        return effectiveConfig.triggerTokens();
    }

    public int triggerMessages() {
        return effectiveConfig.triggerMessages();
    }

    private static OptionalDouble usageRatio(ContextUsage usage) {
        if (usage.contextWindowTokens().isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) usage.totalTokens() / usage.contextWindowTokens().getAsLong());
    }
}
