package com.agent4j.ai;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AiStreamOptions(
        AiAbortSignal signal,
        Optional<Duration> timeout,
        int maxRetries,
        Map<String, String> headers,
        Map<String, Object> attributes,
        AiGenerationOptions generation
) {
    public AiStreamOptions {
        signal = signal == null ? AiAbortSignal.none() : signal;
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(attributes, "attributes");
        generation = generation == null ? AiGenerationOptions.defaults() : generation;
        timeout.ifPresent(value -> {
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
        });
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative");
        }
        headers = Map.copyOf(headers);
        attributes = Map.copyOf(attributes);
    }

    public AiStreamOptions(
            AiAbortSignal signal,
            Optional<Duration> timeout,
            int maxRetries,
            Map<String, String> headers,
            Map<String, Object> attributes
    ) {
        this(signal, timeout, maxRetries, headers, attributes, AiGenerationOptions.defaults());
    }

    public static AiStreamOptions defaults() {
        return new AiStreamOptions(
                AiAbortSignal.none(),
                Optional.empty(),
                0,
                Map.of(),
                Map.of(),
                AiGenerationOptions.defaults());
    }
}
