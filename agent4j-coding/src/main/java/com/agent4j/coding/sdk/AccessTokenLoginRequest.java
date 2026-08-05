package com.agent4j.coding.sdk;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AccessTokenLoginRequest(
        String providerId,
        String accessToken,
        Optional<String> baseUrl,
        Optional<Instant> expiresAt,
        Map<String, String> metadata
) {
    public AccessTokenLoginRequest(String providerId, String accessToken) {
        this(providerId, accessToken, Optional.empty(), Optional.empty(), Map.of());
    }

    public AccessTokenLoginRequest {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(accessToken, "accessToken");
        baseUrl = baseUrl == null ? Optional.empty() : baseUrl;
        expiresAt = expiresAt == null ? Optional.empty() : expiresAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        baseUrl.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("baseUrl must not be blank");
            }
        });
    }
}
