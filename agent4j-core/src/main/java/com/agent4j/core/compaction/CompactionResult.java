package com.agent4j.core.compaction;

import com.agent4j.core.message.AgentMessage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CompactionResult(
        boolean compacted,
        CompactionReason reason,
        AgentMessage summaryMessage,
        List<AgentMessage> retainedMessages,
        ContextUsage usageBefore,
        ContextUsage usageAfter
) {
    public CompactionResult {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(retainedMessages, "retainedMessages");
        Objects.requireNonNull(usageBefore, "usageBefore");
        Objects.requireNonNull(usageAfter, "usageAfter");
        retainedMessages = List.copyOf(retainedMessages);
        if (compacted && summaryMessage == null) {
            throw new IllegalArgumentException("summaryMessage is required when compacted is true");
        }
    }

    public static CompactionResult noOp(CompactionReason reason, ContextUsage usage) {
        return new CompactionResult(false, reason, null, List.of(), usage, usage);
    }

    public CompactionResult(
            CompactionReason reason,
            AgentMessage summaryMessage,
            List<AgentMessage> retainedMessages,
            ContextUsage usageBefore,
            ContextUsage usageAfter
    ) {
        this(true, reason, summaryMessage, retainedMessages, usageBefore, usageAfter);
    }

    public Optional<AgentMessage> optionalSummaryMessage() {
        return Optional.ofNullable(summaryMessage);
    }

    public List<AgentMessage> compactedMessages() {
        if (!compacted) {
            return retainedMessages;
        }
        java.util.ArrayList<AgentMessage> messages = new java.util.ArrayList<>();
        messages.add(summaryMessage);
        messages.addAll(retainedMessages);
        return List.copyOf(messages);
    }
}
