package com.agent4j.core.compaction;

import com.agent4j.core.message.AgentMessage;

import java.util.Objects;

public record BranchSummaryResult(AgentMessage summaryMessage, ContextUsage usageBefore) {
    public BranchSummaryResult {
        Objects.requireNonNull(summaryMessage, "summaryMessage");
        Objects.requireNonNull(usageBefore, "usageBefore");
    }
}
