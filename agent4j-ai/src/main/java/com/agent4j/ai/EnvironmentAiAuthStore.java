package com.agent4j.ai;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EnvironmentAiAuthStore implements AiAuthStore {
    private final Map<String, String> environment;
    private final Map<String, ProviderEnvironmentAuth> providers;

    public EnvironmentAiAuthStore() {
        this(System.getenv(), defaults());
    }

    public EnvironmentAiAuthStore(Map<String, String> environment) {
        this(environment, defaults());
    }

    public EnvironmentAiAuthStore(Map<String, String> environment, Map<String, ProviderEnvironmentAuth> providers) {
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        this.providers = Map.copyOf(Objects.requireNonNull(providers, "providers"));
    }

    @Override
    public Optional<AiResolvedAuth> resolve(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        ProviderEnvironmentAuth config = providers.get(providerId);
        if (config == null) {
            return Optional.empty();
        }
        Optional<String> apiKey = config.apiKeyEnv().flatMap(this::env);
        Optional<String> baseUrl = config.baseUrlEnv().flatMap(this::env);
        if (apiKey.isEmpty() && baseUrl.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AiResolvedAuth(
                apiKey,
                Map.of(),
                baseUrl,
                Optional.of("environment"),
                Map.of()));
    }

    private Optional<String> env(String name) {
        return Optional.ofNullable(environment.get(name)).filter(value -> !value.isBlank());
    }

    public static Map<String, ProviderEnvironmentAuth> defaults() {
        return Map.of(
                "openai", new ProviderEnvironmentAuth(Optional.of("OPENAI_API_KEY"), Optional.of("OPENAI_BASE_URL")),
                "anthropic", new ProviderEnvironmentAuth(Optional.of("ANTHROPIC_API_KEY"), Optional.of("ANTHROPIC_BASE_URL")));
    }

    public record ProviderEnvironmentAuth(Optional<String> apiKeyEnv, Optional<String> baseUrlEnv) {
        public ProviderEnvironmentAuth {
            Objects.requireNonNull(apiKeyEnv, "apiKeyEnv");
            Objects.requireNonNull(baseUrlEnv, "baseUrlEnv");
        }
    }
}
