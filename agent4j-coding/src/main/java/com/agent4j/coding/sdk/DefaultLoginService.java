package com.agent4j.coding.sdk;

import com.agent4j.ai.AiAuthMode;
import com.agent4j.ai.AiResolvedAuth;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class DefaultLoginService implements LoginService {
    private static final Optional<String> SOURCE = Optional.of("sdk-login");

    private final AuthCredentialStore credentialStore;
    private final Clock clock;

    public DefaultLoginService(AuthCredentialStore credentialStore, Clock clock) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AuthSession loginApiKey(ApiKeyLoginRequest request) {
        Objects.requireNonNull(request, "request");
        AuthSession session = new AuthSession(
                request.providerId(),
                AiAuthMode.API_KEY,
                AiResolvedAuth.apiKey(request.apiKey(), request.baseUrl(), SOURCE),
                now());
        credentialStore.save(session);
        return session;
    }

    @Override
    public AuthSession loginAccessToken(AccessTokenLoginRequest request) {
        Objects.requireNonNull(request, "request");
        AuthSession session = new AuthSession(
                request.providerId(),
                AiAuthMode.ACCESS_TOKEN,
                AiResolvedAuth.accessToken(
                        request.accessToken(),
                        request.baseUrl(),
                        SOURCE,
                        request.expiresAt(),
                        request.metadata()),
                now());
        credentialStore.save(session);
        return session;
    }

    @Override
    public AuthStatus status(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return credentialStore.find(providerId)
                .map(session -> new AuthStatus(
                        session.providerId(),
                        session.mode(),
                        true,
                        session.expired(now()),
                        session.expiresAt(),
                        session.auth().source()))
                .orElseGet(() -> AuthStatus.unauthenticated(providerId));
    }

    @Override
    public AiResolvedAuth resolveAuth(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return credentialStore.find(providerId)
                .filter(session -> !session.expired(now()))
                .map(AuthSession::auth)
                .orElseGet(AiResolvedAuth::none);
    }

    @Override
    public boolean logout(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return credentialStore.delete(providerId);
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
