package com.agent4j.coding.message;

import com.agent4j.core.message.AgentMessage;

import java.util.Objects;

public record CompactionSummaryMessage(String summary) {
    public CompactionSummaryMessage {
        summary = summary == null ? "" : summary;
    }

    public static CompactionSummaryMessage from(AgentMessage message) {
        Objects.requireNonNull(message, "message");
        return new CompactionSummaryMessage(CodingAgentMessages.textContent(message));
    }

    public String toLlmText() {
        return "<compactionSummary>\n" + summary + "\n</compactionSummary>";
    }
}
