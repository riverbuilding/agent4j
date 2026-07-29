package com.agent4j.ai;

public record AiUsage(long inputTokens, long outputTokens, long cachedInputTokens, long reasoningTokens) {
    public AiUsage {
        if (inputTokens < 0 || outputTokens < 0 || cachedInputTokens < 0 || reasoningTokens < 0) {
            throw new IllegalArgumentException("token counts must be non-negative");
        }
    }

    public static AiUsage zero() {
        return new AiUsage(0, 0, 0, 0);
    }
}
