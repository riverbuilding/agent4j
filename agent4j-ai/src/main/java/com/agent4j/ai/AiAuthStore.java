package com.agent4j.ai;

import java.util.Objects;
import java.util.Optional;

@FunctionalInterface
public interface AiAuthStore {
    Optional<AiResolvedAuth> resolve(String providerId);

    default AiResolvedAuth require(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return resolve(providerId).orElseGet(AiResolvedAuth::none);
    }

    static AiAuthStore none() {
        return providerId -> Optional.empty();
    }
}
