package com.agent4j.coding.sdk;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record BrowserSubscriptionLoginRequest(
        String providerId,
        Optional<String> baseUrl,
        Map<String, String> metadata
) {
    public BrowserSubscriptionLoginRequest(String providerId) {
        this(providerId, Optional.empty(), Map.of());
    }

    public BrowserSubscriptionLoginRequest {
        Objects.requireNonNull(providerId, "providerId");
        baseUrl = baseUrl == null ? Optional.empty() : baseUrl;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        baseUrl.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("baseUrl must not be blank");
            }
        });
    }
}
