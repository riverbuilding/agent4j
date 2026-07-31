package com.agent4j.ai;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AiResolvedAuth(
        Optional<String> apiKey,
        Map<String, String> headers,
        Optional<String> baseUrl,
        Optional<String> source,
        Map<String, String> environment
) {
    public AiResolvedAuth {
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(environment, "environment");
        headers = Map.copyOf(headers);
        environment = Map.copyOf(environment);
    }

    public static AiResolvedAuth none() {
        return new AiResolvedAuth(Optional.empty(), Map.of(), Optional.empty(), Optional.empty(), Map.of());
    }
}
