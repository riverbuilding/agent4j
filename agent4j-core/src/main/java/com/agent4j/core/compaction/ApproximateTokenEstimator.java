package com.agent4j.core.compaction;

public final class ApproximateTokenEstimator implements TokenEstimator {
    private static final int CHARS_PER_TOKEN = 4;
    private static final long MESSAGE_OVERHEAD_TOKENS = 4;

    @Override
    public long estimateText(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN);
    }

    @Override
    public long estimateMessage(com.agent4j.core.message.AgentMessage message) {
        return MESSAGE_OVERHEAD_TOKENS + TokenEstimator.super.estimateMessage(message);
    }
}
