package com.agent4j.core.compaction;

import com.agent4j.core.message.AgentMessage;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

public record ContextUsage(
        long systemPromptTokens,
        long messageTokens,
        int messageCount,
        OptionalLong contextWindowTokens
) {
    public ContextUsage {
        if (systemPromptTokens < 0 || messageTokens < 0) {
            throw new IllegalArgumentException("token counts must be non-negative");
        }
        if (messageCount < 0) {
            throw new IllegalArgumentException("messageCount must be non-negative");
        }
        Objects.requireNonNull(contextWindowTokens, "contextWindowTokens");
        contextWindowTokens.ifPresent(value -> {
            if (value <= 0) {
                throw new IllegalArgumentException("contextWindowTokens must be positive when present");
            }
        });
    }

    public static ContextUsage calculate(
            String systemPrompt,
            List<AgentMessage> messages,
            TokenEstimator tokenEstimator,
            OptionalLong contextWindowTokens
    ) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(tokenEstimator, "tokenEstimator");
        long systemTokens = systemPrompt == null || systemPrompt.isBlank()
                ? 0
                : tokenEstimator.estimateText(systemPrompt);
        long messageTokens = tokenEstimator.estimateMessages(messages);
        return new ContextUsage(systemTokens, messageTokens, messages.size(), contextWindowTokens);
    }

    public long totalTokens() {
        return systemPromptTokens + messageTokens;
    }

    public OptionalLong remainingTokens() {
        if (contextWindowTokens.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.max(0, contextWindowTokens.getAsLong() - totalTokens()));
    }

    public boolean exceeds(long tokenBudget) {
        if (tokenBudget < 0) {
            throw new IllegalArgumentException("tokenBudget must be non-negative");
        }
        return totalTokens() >= tokenBudget;
    }
}
