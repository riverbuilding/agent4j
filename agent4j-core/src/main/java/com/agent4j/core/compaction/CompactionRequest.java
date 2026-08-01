package com.agent4j.core.compaction;

import com.agent4j.core.message.AgentMessage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CompactionRequest(
        String sessionId,
        CompactionReason reason,
        List<AgentMessage> messages,
        String systemPrompt,
        CompactionConfig config,
        String focusInstructions
) {
    public CompactionRequest {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(config, "config");
        messages = List.copyOf(messages);
    }

    public Optional<String> optionalSystemPrompt() {
        return systemPrompt == null || systemPrompt.isBlank() ? Optional.empty() : Optional.of(systemPrompt);
    }

    public Optional<String> optionalFocusInstructions() {
        return focusInstructions == null || focusInstructions.isBlank() ? Optional.empty() : Optional.of(focusInstructions);
    }
}
