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
    private final SubscriptionLoginClient subscriptionLoginClient;

    public DefaultLoginService(AuthCredentialStore credentialStore, Clock clock) {
        this(credentialStore, clock, SubscriptionLoginClient.unsupported());
    }

    public DefaultLoginService(
            AuthCredentialStore credentialStore,
            Clock clock,
            SubscriptionLoginClient subscriptionLoginClient
    ) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.subscriptionLoginClient = Objects.requireNonNull(subscriptionLoginClient, "subscriptionLoginClient");
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
    public SubscriptionLoginStart startBrowserSubscriptionLogin(BrowserSubscriptionLoginRequest request) {
        Objects.requireNonNull(request, "request");
        return subscriptionLoginClient.startBrowserLogin(request, now());
    }

    @Override
    public SubscriptionLoginStart startDeviceCodeSubscriptionLogin(DeviceCodeSubscriptionLoginRequest request) {
        Objects.requireNonNull(request, "request");
        return subscriptionLoginClient.startDeviceCodeLogin(request, now());
    }

    @Override
    public AuthSession completeSubscriptionLogin(SubscriptionLoginCompletion completion) {
        Objects.requireNonNull(completion, "completion");
        AuthSession session = new AuthSession(
                completion.providerId(),
                AiAuthMode.CHATGPT_SUBSCRIPTION,
                AiResolvedAuth.chatGptSubscription(
                        completion.accessToken(),
                        completion.baseUrl(),
                        SOURCE,
                        completion.expiresAt(),
                        completion.metadata()),
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
