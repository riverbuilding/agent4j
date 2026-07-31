package com.agent4j.ai;

import java.util.List;
import java.util.Objects;

public record AiCost(AiTokenCost base, List<AiCostTier> tiers) {
    public AiCost {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(tiers, "tiers");
        tiers = List.copyOf(tiers);
    }

    public static AiCost zero() {
        return new AiCost(AiTokenCost.zero(), List.of());
    }
}
