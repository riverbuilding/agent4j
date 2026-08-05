package com.agent4j.coding.sdk;

import com.agent4j.ai.AiAuthMode;
import com.agent4j.ai.AiResolvedAuth;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record AuthSession(
        String providerId,
        AiAuthMode mode,
        AiResolvedAuth auth,
        Instant authenticatedAt
) {
    public AuthSession {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(authenticatedAt, "authenticatedAt");
        if (providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
    }

    public Optional<Instant> expiresAt() {
        return auth.expiresAt();
    }

    public boolean expired(Instant now) {
        Objects.requireNonNull(now, "now");
        return expiresAt().map(expiresAt -> !expiresAt.isAfter(now)).orElse(false);
    }
}
