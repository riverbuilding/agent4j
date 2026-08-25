package com.agent4j.ai;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AiResolvedAuth(
        AiAuthMode mode,
        Optional<String> apiKey,
        Optional<String> accessToken,
        Map<String, String> headers,
        Optional<String> baseUrl,
        Optional<String> source,
        Optional<Instant> expiresAt,
        Map<String, String> environment,
        Map<String, String> metadata
) {
    public AiResolvedAuth {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(metadata, "metadata");
        mode = normalizeMode(mode, apiKey, accessToken, headers, baseUrl);
        headers = Map.copyOf(headers);
        environment = Map.copyOf(environment);
        metadata = Map.copyOf(metadata);
    }

    public AiResolvedAuth(
            Optional<String> apiKey,
            Map<String, String> headers,
            Optional<String> baseUrl,
            Optional<String> source,
            Map<String, String> environment
    ) {
        this(
                inferMode(apiKey, Optional.empty(), headers, baseUrl),
                apiKey,
                Optional.empty(),
                headers,
                baseUrl,
                source,
                Optional.empty(),
                environment,
                Map.of());
    }

    public static AiResolvedAuth none() {
        return new AiResolvedAuth(
                AiAuthMode.NONE,
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                Map.of());
    }

    public static AiResolvedAuth apiKey(String apiKey, Optional<String> baseUrl, Optional<String> source) {
        return new AiResolvedAuth(
                AiAuthMode.API_KEY,
                Optional.of(apiKey),
                Optional.empty(),
                Map.of(),
                baseUrl,
                source,
                Optional.empty(),
                Map.of(),
                Map.of());
    }

    public static AiResolvedAuth accessToken(
            String accessToken,
            Optional<String> baseUrl,
            Optional<String> source,
            Optional<Instant> expiresAt,
            Map<String, String> metadata
    ) {
        return new AiResolvedAuth(
                AiAuthMode.ACCESS_TOKEN,
                Optional.empty(),
                Optional.of(accessToken),
                Map.of(),
                baseUrl,
                source,
                expiresAt,
                Map.of(),
                metadata);
    }

    public static AiResolvedAuth chatGptSubscription(
            String accessToken,
            Optional<String> baseUrl,
            Optional<String> source,
            Optional<Instant> expiresAt,
            Map<String, String> metadata
    ) {
        return new AiResolvedAuth(
                AiAuthMode.CHATGPT_SUBSCRIPTION,
                Optional.empty(),
                Optional.of(accessToken),
                Map.of(),
                baseUrl,
                source,
                expiresAt,
                Map.of(),
                metadata);
    }

    public Optional<String> authorizationBearerToken() {
        return accessToken.or(() -> apiKey);
    }

    public boolean hasCredentials() {
        return apiKey.isPresent() || accessToken.isPresent() || !headers.isEmpty() || baseUrl.isPresent();
    }

    /** Returns whether this resolution includes credentials that can authenticate a request. */
    public boolean hasAuthentication() {
        return apiKey.isPresent() || accessToken.isPresent() || !headers.isEmpty();
    }

    private static AiAuthMode normalizeMode(
            AiAuthMode mode,
            Optional<String> apiKey,
            Optional<String> accessToken,
            Map<String, String> headers,
            Optional<String> baseUrl
    ) {
        if (mode != AiAuthMode.NONE) {
            return mode;
        }
        return inferMode(apiKey, accessToken, headers, baseUrl);
    }

    private static AiAuthMode inferMode(
            Optional<String> apiKey,
            Optional<String> accessToken,
            Map<String, String> headers,
            Optional<String> baseUrl
    ) {
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(baseUrl, "baseUrl");
        if (accessToken.isPresent()) {
            return AiAuthMode.ACCESS_TOKEN;
        }
        if (apiKey.isPresent()) {
            return AiAuthMode.API_KEY;
        }
        if (!headers.isEmpty()) {
            return AiAuthMode.CUSTOM_HEADERS;
        }
        return baseUrl.isPresent() ? AiAuthMode.CUSTOM_HEADERS : AiAuthMode.NONE;
    }
}
