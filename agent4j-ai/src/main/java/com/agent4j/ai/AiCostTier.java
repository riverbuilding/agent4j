package com.agent4j.ai;

import java.util.Objects;

public record AiCostTier(long inputTokensAbove, AiTokenCost cost) {
    public AiCostTier {
        if (inputTokensAbove < 0) {
            throw new IllegalArgumentException("inputTokensAbove must be non-negative");
        }
        Objects.requireNonNull(cost, "cost");
    }
}
