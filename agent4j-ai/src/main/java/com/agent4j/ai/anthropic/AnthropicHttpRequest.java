package com.agent4j.ai.anthropic;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AnthropicHttpRequest(
        URI uri,
        Map<String, String> headers,
        String body,
        Optional<Duration> timeout
) {
    public AnthropicHttpRequest {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(timeout, "timeout");
        headers = Map.copyOf(headers);
    }
}
