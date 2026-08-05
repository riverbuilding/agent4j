package com.agent4j.coding.sdk;

import com.agent4j.ai.AiAuthMode;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record AuthStatus(
        String providerId,
        AiAuthMode mode,
        boolean authenticated,
        boolean expired,
        Optional<Instant> expiresAt,
        Optional<String> source
) {
    public AuthStatus {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(source, "source");
    }

    public static AuthStatus unauthenticated(String providerId) {
        return new AuthStatus(providerId, AiAuthMode.NONE, false, false, Optional.empty(), Optional.empty());
    }
}
