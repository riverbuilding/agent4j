package com.agent4j.core.compaction;

import com.agent4j.core.message.AgentMessage;

import java.util.List;
import java.util.Objects;

public record CompactionPlan(
        boolean compact,
        CompactionReason reason,
        int cutoffIndex,
        List<AgentMessage> prefixMessages,
        List<AgentMessage> retainedMessages,
        ContextUsage usage,
        CompactionConfig effectiveConfig
) {
    public CompactionPlan {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(prefixMessages, "prefixMessages");
        Objects.requireNonNull(retainedMessages, "retainedMessages");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(effectiveConfig, "effectiveConfig");
        if (cutoffIndex < 0) {
            throw new IllegalArgumentException("cutoffIndex must be non-negative");
        }
        prefixMessages = List.copyOf(prefixMessages);
        retainedMessages = List.copyOf(retainedMessages);
        if (!compact && (!prefixMessages.isEmpty() || !retainedMessages.isEmpty())) {
            throw new IllegalArgumentException("no-op compaction plan cannot carry prefix or retained messages");
        }
        if (compact && prefixMessages.isEmpty()) {
            throw new IllegalArgumentException("prefixMessages are required when compact is true");
        }
    }

    public static CompactionPlan noOp(
            CompactionReason reason,
            ContextUsage usage,
            CompactionConfig effectiveConfig
    ) {
        return new CompactionPlan(false, reason, 0, List.of(), List.of(), usage, effectiveConfig);
    }
}
