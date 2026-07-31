package com.agent4j.ai.openai;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record OpenAiHttpRequest(
        URI uri,
        Map<String, String> headers,
        String body,
        Optional<Duration> timeout
) {
    public OpenAiHttpRequest {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(timeout, "timeout");
        headers = Map.copyOf(headers);
    }
}
