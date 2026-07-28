package com.agent4j.core.runtime;

public record Usage(long inputTokens, long outputTokens, long cachedInputTokens, long reasoningTokens) {
    public Usage {
        if (inputTokens < 0 || outputTokens < 0 || cachedInputTokens < 0 || reasoningTokens < 0) {
            throw new IllegalArgumentException("token counts must be non-negative");
        }
    }

    public static Usage zero() {
        return new Usage(0, 0, 0, 0);
    }

    public Usage plus(Usage other) {
        return new Usage(
                inputTokens + other.inputTokens,
                outputTokens + other.outputTokens,
                cachedInputTokens + other.cachedInputTokens,
                reasoningTokens + other.reasoningTokens);
    }

    public long totalTokens() {
        return inputTokens + outputTokens + reasoningTokens;
    }
}
