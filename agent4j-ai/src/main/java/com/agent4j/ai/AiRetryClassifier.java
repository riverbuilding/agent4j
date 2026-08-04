package com.agent4j.ai;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.http.HttpTimeoutException;
import java.util.Objects;

public final class AiRetryClassifier {
    private AiRetryClassifier() {
    }

    public static boolean isRetryable(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable");
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AiProviderHttpException providerHttpException) {
                return providerHttpException.retryable();
            }
            if (current instanceof HttpTimeoutException
                    || current instanceof InterruptedIOException
                    || current instanceof ConnectException
                    || current instanceof SocketException
                    || current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
