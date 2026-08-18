package com.agent4j.coding.extension;

import java.util.Map;
import java.util.Objects;

/** Provider response metadata exposed before the response stream is consumed. */
public record CodingExtensionProviderResponse(int status, Map<String, String> headers) {
    public CodingExtensionProviderResponse {
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
    }
}
