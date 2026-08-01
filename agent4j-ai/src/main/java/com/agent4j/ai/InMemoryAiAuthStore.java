package com.agent4j.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryAiAuthStore implements AiAuthStore {
    private final Map<String, AiResolvedAuth> authByProviderId;

    public InMemoryAiAuthStore(Map<String, AiResolvedAuth> authByProviderId) {
        Objects.requireNonNull(authByProviderId, "authByProviderId");
        this.authByProviderId = Map.copyOf(authByProviderId);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<AiResolvedAuth> resolve(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return Optional.ofNullable(authByProviderId.get(providerId));
    }

    public static final class Builder {
        private final Map<String, AiResolvedAuth> authByProviderId = new LinkedHashMap<>();

        public Builder put(String providerId, AiResolvedAuth auth) {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(auth, "auth");
            if (providerId.isBlank()) {
                throw new IllegalArgumentException("providerId must not be blank");
            }
            authByProviderId.put(providerId, auth);
            return this;
        }

        public InMemoryAiAuthStore build() {
            return new InMemoryAiAuthStore(authByProviderId);
        }
    }
}
