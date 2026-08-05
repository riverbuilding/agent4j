package com.agent4j.coding.sdk;

import java.util.Objects;
import java.util.Optional;

public record ApiKeyLoginRequest(
        String providerId,
        String apiKey,
        Optional<String> baseUrl
) {
    public ApiKeyLoginRequest(String providerId, String apiKey) {
        this(providerId, apiKey, Optional.empty());
    }

    public ApiKeyLoginRequest {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(apiKey, "apiKey");
        baseUrl = baseUrl == null ? Optional.empty() : baseUrl;
        if (providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        baseUrl.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("baseUrl must not be blank");
            }
        });
    }
}
