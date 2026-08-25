package com.agent4j.ai.gemini;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record GeminiHttpRequest(URI uri, Map<String, String> headers, String body, Optional<Duration> timeout) {
    public GeminiHttpRequest {
        Objects.requireNonNull(uri, "uri");
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
        Objects.requireNonNull(body, "body");
        timeout = timeout == null ? Optional.empty() : timeout;
    }
}
