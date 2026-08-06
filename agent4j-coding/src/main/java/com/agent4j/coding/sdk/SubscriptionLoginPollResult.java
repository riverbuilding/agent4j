package com.agent4j.coding.sdk;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record SubscriptionLoginPollResult(
        SubscriptionLoginStatus status,
        Optional<SubscriptionLoginCompletion> completion,
        Optional<String> error,
        Optional<Instant> retryAfter
) {
    public SubscriptionLoginPollResult {
        Objects.requireNonNull(status, "status");
        completion = completion == null ? Optional.empty() : completion;
        error = error == null ? Optional.empty() : error;
        retryAfter = retryAfter == null ? Optional.empty() : retryAfter;
        if (status == SubscriptionLoginStatus.COMPLETED && completion.isEmpty()) {
            throw new IllegalArgumentException("completed login poll result must include completion");
        }
        if (status != SubscriptionLoginStatus.COMPLETED && completion.isPresent()) {
            throw new IllegalArgumentException("only completed login poll result can include completion");
        }
    }

    public static SubscriptionLoginPollResult pending(Optional<Instant> retryAfter) {
        return new SubscriptionLoginPollResult(
                SubscriptionLoginStatus.PENDING,
                Optional.empty(),
                Optional.empty(),
                retryAfter);
    }

    public static SubscriptionLoginPollResult completed(SubscriptionLoginCompletion completion) {
        return new SubscriptionLoginPollResult(
                SubscriptionLoginStatus.COMPLETED,
                Optional.of(completion),
                Optional.empty(),
                Optional.empty());
    }

    public static SubscriptionLoginPollResult failed(String error) {
        return new SubscriptionLoginPollResult(
                SubscriptionLoginStatus.FAILED,
                Optional.empty(),
                Optional.of(error == null || error.isBlank() ? "subscription login failed" : error),
                Optional.empty());
    }

    public static SubscriptionLoginPollResult expired(String error) {
        return new SubscriptionLoginPollResult(
                SubscriptionLoginStatus.EXPIRED,
                Optional.empty(),
                Optional.of(error == null || error.isBlank() ? "subscription login expired" : error),
                Optional.empty());
    }
}
