package com.agent4j.coding.sdk;

import com.agent4j.ai.AiAuthMode;
import com.agent4j.ai.AiResolvedAuth;

import java.io.IOException;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class DefaultLoginService implements LoginService {
    private static final String OPENAI_PROVIDER_ID = "openai";
    private static final Duration DEFAULT_BROWSER_LOGIN_TIMEOUT = Duration.ofMinutes(10);
    private static final Optional<String> SOURCE = Optional.of("sdk-login");

    private final AuthCredentialStore credentialStore;
    private final Clock clock;
    private final SubscriptionLoginClient subscriptionLoginClient;
    private final BrowserLauncher browserLauncher;

    public DefaultLoginService(AuthCredentialStore credentialStore, Clock clock) {
        this(credentialStore, clock, SubscriptionLoginClient.unsupported(), BrowserLauncher.system());
    }

    public DefaultLoginService(
            AuthCredentialStore credentialStore,
            Clock clock,
            SubscriptionLoginClient subscriptionLoginClient
    ) {
        this(credentialStore, clock, subscriptionLoginClient, BrowserLauncher.system());
    }

    DefaultLoginService(
            AuthCredentialStore credentialStore,
            Clock clock,
            SubscriptionLoginClient subscriptionLoginClient,
            BrowserLauncher browserLauncher
    ) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.subscriptionLoginClient = Objects.requireNonNull(subscriptionLoginClient, "subscriptionLoginClient");
        this.browserLauncher = Objects.requireNonNull(browserLauncher, "browserLauncher");
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
    public AuthStatus loginOpenAiSubscription() throws IOException {
        String flowId = null;
        try (BrowserSubscriptionLoginCallbackServer callbackServer =
                     BrowserSubscriptionLoginCallbackServer.startDefaultBrowserCallback(this)) {
            SubscriptionLoginStart start = startBrowserSubscriptionLogin(new BrowserSubscriptionLoginRequest(
                    OPENAI_PROVIDER_ID, Optional.empty(), Map.of(), Optional.of(callbackServer.redirectUri())));
            flowId = start.flowId();
            browserLauncher.open(start.authorizationUri());
            SubscriptionLoginPollResult result = awaitBrowserLogin(callbackServer, start);
            if (result.status() != SubscriptionLoginStatus.COMPLETED) {
                throw new IllegalStateException(result.error().orElse("OpenAI subscription login failed"));
            }
            return status(OPENAI_PROVIDER_ID);
        } finally {
            if (flowId != null) {
                cancelSubscriptionLogin(flowId);
            }
        }
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
    public SubscriptionLoginPollResult pollSubscriptionLogin(String flowId) {
        Objects.requireNonNull(flowId, "flowId");
        SubscriptionLoginPollResult result = subscriptionLoginClient.pollLogin(flowId, now());
        result.completion().ifPresent(this::completeSubscriptionLogin);
        return result;
    }

    @Override
    public SubscriptionLoginPollResult completeBrowserSubscriptionLoginCallback(String code, String state) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(state, "state");
        SubscriptionLoginPollResult result = subscriptionLoginClient.completeBrowserLoginCallback(code, state, now());
        result.completion().ifPresent(this::completeSubscriptionLogin);
        return result;
    }

    @Override
    public SubscriptionLoginPollResult completeBrowserSubscriptionLoginErrorCallback(
            String error,
            Optional<String> state
    ) {
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(state, "state");
        return subscriptionLoginClient.completeBrowserLoginErrorCallback(error, state, now());
    }

    @Override
    public boolean cancelSubscriptionLogin(String flowId) {
        Objects.requireNonNull(flowId, "flowId");
        return subscriptionLoginClient.cancelLogin(flowId, now());
    }

    @Override
    public Optional<AuthSession> refreshAuth(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return credentialStore.find(providerId)
                .flatMap(this::refreshSession);
    }

    @Override
    public AuthStatus status(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return credentialStore.find(providerId)
                .map(this::refreshIfExpired)
                .map(session -> new AuthStatus(
                        session.providerId(),
                        session.mode(),
                        true,
                        session.expired(now()),
                        session.expiresAt(),
                        session.auth().source(),
                        session.auth().metadata()))
                .orElseGet(() -> AuthStatus.unauthenticated(providerId));
    }

    @Override
    public AiResolvedAuth resolveAuth(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return credentialStore.find(providerId)
                .map(this::refreshIfExpired)
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

    private SubscriptionLoginPollResult awaitBrowserLogin(
            BrowserSubscriptionLoginCallbackServer callbackServer,
            SubscriptionLoginStart start
    ) throws IOException {
        Instant expiresAt = start.expiresAt().orElseGet(() -> now().plus(DEFAULT_BROWSER_LOGIN_TIMEOUT));
        long timeoutMillis = Math.max(1, Duration.between(now(), expiresAt).toMillis());
        try {
            return callbackServer.completion().get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IOException("OpenAI subscription login timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OpenAI subscription login was cancelled", e);
        } catch (ExecutionException e) {
            throw new IOException("OpenAI subscription login callback failed", e.getCause());
        }
    }

    private AuthSession refreshIfExpired(AuthSession session) {
        if (!session.expired(now())) {
            return session;
        }
        return refreshSession(session).orElse(session);
    }

    private Optional<AuthSession> refreshSession(AuthSession session) {
        if (session.mode() != AiAuthMode.CHATGPT_SUBSCRIPTION) {
            return Optional.empty();
        }
        try {
            return subscriptionLoginClient.refreshLogin(session, now())
                    .map(this::completeSubscriptionLogin);
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }
}
