package com.agent4j.coding.message;

import com.agent4j.core.message.AgentMessage;

import java.util.Objects;

public record BranchSummaryMessage(String summary) {
    public BranchSummaryMessage {
        summary = summary == null ? "" : summary;
    }

    public static BranchSummaryMessage from(AgentMessage message) {
        Objects.requireNonNull(message, "message");
        return new BranchSummaryMessage(CodingAgentMessages.textContent(message));
    }

    public String toLlmText() {
        return "<branchSummary>\n" + summary + "\n</branchSummary>";
    }
}
