package com.agent4j.ai;

public record AiTokenCost(
        double input,
        double output,
        double cacheRead,
        double cacheWrite
) {
    public AiTokenCost {
        if (input < 0 || output < 0 || cacheRead < 0 || cacheWrite < 0) {
            throw new IllegalArgumentException("token costs must be non-negative");
        }
    }

    public static AiTokenCost zero() {
        return new AiTokenCost(0, 0, 0, 0);
    }
}
