package com.agent4j.coding.message;

import com.agent4j.core.message.CustomAgentMessageView;

import java.util.Objects;

public record CompactionSummaryMessage(String summary) {
    public CompactionSummaryMessage {
        summary = summary == null ? "" : summary;
    }

    public static CompactionSummaryMessage from(CustomAgentMessageView message) {
        Objects.requireNonNull(message, "message");
        return new CompactionSummaryMessage(message.text());
    }

    public String toLlmText() {
        return "<compactionSummary>\n" + summary + "\n</compactionSummary>";
    }
}
