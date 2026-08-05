package com.agent4j.coding.sdk;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record SubscriptionLoginStart(
        String providerId,
        SubscriptionLoginMode mode,
        String flowId,
        URI authorizationUri,
        Optional<URI> verificationUri,
        Optional<String> userCode,
        Optional<Instant> expiresAt,
        Map<String, String> metadata
) {
    public SubscriptionLoginStart {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(flowId, "flowId");
        Objects.requireNonNull(authorizationUri, "authorizationUri");
        verificationUri = verificationUri == null ? Optional.empty() : verificationUri;
        userCode = userCode == null ? Optional.empty() : userCode;
        expiresAt = expiresAt == null ? Optional.empty() : expiresAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (flowId.isBlank()) {
            throw new IllegalArgumentException("flowId must not be blank");
        }
    }
}
