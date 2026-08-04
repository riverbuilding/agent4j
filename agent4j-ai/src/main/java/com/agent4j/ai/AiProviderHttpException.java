package com.agent4j.ai;

import java.io.IOException;
import java.util.Objects;

public final class AiProviderHttpException extends IOException {
    private final String providerId;
    private final int statusCode;
    private final String responseBody;

    public AiProviderHttpException(String providerId, int statusCode, String responseBody) {
        super(providerId + " request failed with HTTP " + statusCode + ": " + responseBody);
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.statusCode = statusCode;
        this.responseBody = responseBody == null ? "" : responseBody;
    }

    public String providerId() {
        return providerId;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }

    public boolean retryable() {
        return statusCode == 408
                || statusCode == 409
                || statusCode == 425
                || statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }
}
