package com.agent4j.coding.message;

import com.agent4j.core.message.CustomAgentMessageView;

import java.util.Objects;

public record BranchSummaryMessage(String summary) {
    public BranchSummaryMessage {
        summary = summary == null ? "" : summary;
    }

    public static BranchSummaryMessage from(CustomAgentMessageView message) {
        Objects.requireNonNull(message, "message");
        return new BranchSummaryMessage(message.text());
    }

    public String toLlmText() {
        return "<branchSummary>\n" + summary + "\n</branchSummary>";
    }
}
