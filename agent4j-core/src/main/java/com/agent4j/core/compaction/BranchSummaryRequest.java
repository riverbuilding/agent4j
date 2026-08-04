package com.agent4j.core.compaction;

import com.agent4j.core.message.AgentMessage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record BranchSummaryRequest(
        String sessionId,
        List<AgentMessage> messages,
        String systemPrompt,
        String summaryPrompt,
        String focusInstructions,
        String sourceEntryId,
        String targetSessionId
) {
    public static final String DEFAULT_SUMMARY_PROMPT = """
            Summarize the conversation branch below so a forked or resumed branch can continue with the important context.

            Conversation:
            {messages}

            Write a concise branch summary preserving user intent, files, decisions, tool results, and unresolved work.""";

    public BranchSummaryRequest {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(messages, "messages");
        messages = List.copyOf(messages);
        summaryPrompt = summaryPrompt == null || summaryPrompt.isBlank() ? DEFAULT_SUMMARY_PROMPT : summaryPrompt;
        if (!summaryPrompt.contains("{messages}")) {
            throw new IllegalArgumentException("summaryPrompt must contain {messages}");
        }
    }

    public BranchSummaryRequest(String sessionId, List<AgentMessage> messages) {
        this(sessionId, messages, null, null, null, null, null);
    }

    public Optional<String> optionalSystemPrompt() {
        return systemPrompt == null || systemPrompt.isBlank() ? Optional.empty() : Optional.of(systemPrompt);
    }

    public Optional<String> optionalFocusInstructions() {
        return focusInstructions == null || focusInstructions.isBlank() ? Optional.empty() : Optional.of(focusInstructions);
    }

    public Optional<String> optionalSourceEntryId() {
        return sourceEntryId == null || sourceEntryId.isBlank() ? Optional.empty() : Optional.of(sourceEntryId);
    }

    public Optional<String> optionalTargetSessionId() {
        return targetSessionId == null || targetSessionId.isBlank() ? Optional.empty() : Optional.of(targetSessionId);
    }
}
