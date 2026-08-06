package com.agent4j.coding.sdk;

import com.agent4j.ai.AiAuthMode;
import com.agent4j.ai.AiResolvedAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiSubscriptionLoginClientTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void browserLoginBuildsAuthorizationUrlAndExchangesCodeWithPkce() {
        FakeLoginTransport transport = new FakeLoginTransport()
                .enqueue(object()
                        .put("access_token", "subscription-token")
                        .put("expires_in", 3600)
                        .put("token_type", "Bearer")
                        .put("refresh_token", "refresh-token")
                        .put("plan_type", "plus"));
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(options(), transport);

        SubscriptionLoginStart start = client.startBrowserLogin(
                new BrowserSubscriptionLoginRequest("openai", Optional.empty(), Map.of("account", "personal")),
                NOW);
        String state = queryValue(start.authorizationUri(), "state");
        SubscriptionLoginPollResult result = client.completeBrowserLoginCallback("auth-code", state, NOW.plusSeconds(10));

        assertThat(start.mode()).isEqualTo(SubscriptionLoginMode.BROWSER);
        assertThat(start.authorizationUri().toString()).startsWith("https://auth.example.test/authorize?");
        assertThat(start.authorizationUri().getRawQuery()).contains("response_type=code");
        assertThat(start.authorizationUri().getRawQuery()).contains("client_id=codex-client");
        assertThat(start.authorizationUri().getRawQuery()).contains("redirect_uri=http%3A%2F%2Flocalhost%3A1455%2Fauth%2Fcallback");
        assertThat(start.authorizationUri().getRawQuery()).contains("code_challenge_method=S256");
        assertThat(result.status()).isEqualTo(SubscriptionLoginStatus.COMPLETED);
        SubscriptionLoginCompletion completion = result.completion().orElseThrow();
        assertThat(completion.providerId()).isEqualTo("openai");
        assertThat(completion.accessToken()).isEqualTo("subscription-token");
        assertThat(completion.expiresAt()).contains(NOW.plusSeconds(3610));
        assertThat(completion.metadata()).containsEntry("refreshToken", "refresh-token");
        assertThat(completion.metadata()).containsEntry("plan", "plus");
        assertThat(transport.requests()).hasSize(1);
        assertThat(transport.requests().getFirst().endpoint()).isEqualTo(URI.create("https://auth.example.test/token"));
        assertThat(transport.requests().getFirst().form()).containsEntry("grant_type", "authorization_code");
        assertThat(transport.requests().getFirst().form()).containsEntry("code", "auth-code");
        assertThat(transport.requests().getFirst().form()).containsKey("code_verifier");
    }

    @Test
    void loginOpenAiSubscriptionLaunchesBrowserCompletesCallbackAndReturnsPersistedStatus() throws Exception {
        FakeLoginTransport transport = new FakeLoginTransport()
                .enqueue(object()
                        .put("access_token", "browser-token")
                        .put("expires_in", 1800)
                        .put("plan", "plus"));
        List<URI> opened = new ArrayList<>();
        BrowserLauncher browserLauncher = uri -> {
            opened.add(uri);
            URI callback = URI.create(queryValue(uri, "redirect_uri")
                    + "?code=browser-code&state=" + queryValue(uri, "state"));
            try {
                HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(callback).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("callback request interrupted", e);
            }
        };
        LoginService service = new DefaultLoginService(
                new InMemoryAuthCredentialStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OpenAiSubscriptionLoginClient(options(), transport),
                browserLauncher);

        BrowserSubscriptionLoginCallbackServer probe;
        try {
            probe = BrowserSubscriptionLoginCallbackServer.startDefaultBrowserCallback(service);
        } catch (IOException e) {
            Assumptions.assumeTrue(false, "local callback socket binding is not available: " + e.getMessage());
            return;
        }
        probe.close();
        AuthStatus status = service.loginOpenAiSubscription();

        assertThat(opened).hasSize(1);
        assertThat(status.providerId()).isEqualTo("openai");
        assertThat(status.authenticated()).isTrue();
        assertThat(status.mode()).isEqualTo(AiAuthMode.CHATGPT_SUBSCRIPTION);
        assertThat(service.resolveAuth("openai").accessToken()).contains("browser-token");
    }

    @Test
    void codexDefaultsDescribeProductionChatGptLoginProfile() {
        OpenAiSubscriptionLoginClientOptions options = OpenAiSubscriptionLoginClientOptions.codexDefaults();

        assertThat(options.clientId()).isEqualTo("app_EMoamEEZ73f0CkXaXp7hrann");
        assertThat(options.authorizationEndpoint())
                .isEqualTo(URI.create("https://auth.openai.com/oauth/authorize"));
        assertThat(options.tokenEndpoint())
                .isEqualTo(URI.create("https://auth.openai.com/oauth/token"));
        assertThat(options.deviceAuthorizationEndpoint()).contains(
                URI.create("https://auth.openai.com/api/accounts/deviceauth/usercode"));
        assertThat(options.redirectUri()).contains(URI.create("http://localhost:1455/auth/callback"));
        assertThat(options.baseUrl()).contains("https://chatgpt.com/backend-api/codex");
        assertThat(options.scopes()).containsExactly(
                "openid", "profile", "email", "offline_access",
                "api.connectors.read", "api.connectors.invoke");
    }

    @Test
    void codexDefaultsAddProductionAuthorizationParameters() {
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(
                OpenAiSubscriptionLoginClientOptions.codexDefaults(),
                new FakeLoginTransport());

        SubscriptionLoginStart start = client.startBrowserLogin(
                new BrowserSubscriptionLoginRequest("openai"), NOW);

        assertThat(start.authorizationUri().getRawQuery())
                .contains("id_token_add_organizations=true")
                .contains("codex_cli_simplified_flow=true")
                .contains("originator=codex_cli_rs");
    }

    @Test
    void browserLoginRejectsUnknownOrMismatchedState() {
        FakeLoginTransport transport = new FakeLoginTransport();
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(options(), transport);
        SubscriptionLoginStart start = client.startBrowserLogin(new BrowserSubscriptionLoginRequest("openai"), NOW);

        SubscriptionLoginPollResult unknown = client.completeBrowserLoginCallback(
                "auth-code",
                "unknown-state",
                NOW.plusSeconds(10));
        SubscriptionLoginPollResult mismatched = client.completeBrowserLogin(
                start.flowId(),
                "auth-code",
                "wrong-state",
                NOW.plusSeconds(10));

        assertThat(unknown.status()).isEqualTo(SubscriptionLoginStatus.FAILED);
        assertThat(unknown.error()).contains("unknown browser subscription login state");
        assertThat(mismatched.status()).isEqualTo(SubscriptionLoginStatus.FAILED);
        assertThat(mismatched.error().orElseThrow()).contains("state mismatch");
        assertThat(transport.requests()).isEmpty();
    }

    @Test
    void expiredBrowserLoginRemovesStateMapping() {
        FakeLoginTransport transport = new FakeLoginTransport();
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(options(), transport);
        SubscriptionLoginStart start = client.startBrowserLogin(new BrowserSubscriptionLoginRequest("openai"), NOW);
        String state = queryValue(start.authorizationUri(), "state");

        SubscriptionLoginPollResult expired = client.completeBrowserLoginCallback(
                "auth-code",
                state,
                start.expiresAt().orElseThrow().plusSeconds(1));
        SubscriptionLoginPollResult second = client.completeBrowserLoginCallback(
                "auth-code",
                state,
                start.expiresAt().orElseThrow().plusSeconds(2));

        assertThat(expired.status()).isEqualTo(SubscriptionLoginStatus.EXPIRED);
        assertThat(second.status()).isEqualTo(SubscriptionLoginStatus.FAILED);
        assertThat(second.error()).contains("unknown browser subscription login state");
        assertThat(transport.requests()).isEmpty();
    }

    @Test
    void tokenResponseRejectsBlankAccessTokenAndRemovesBrowserFlow() {
        FakeLoginTransport transport = new FakeLoginTransport()
                .enqueue(object().put("access_token", " "));
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(options(), transport);
        SubscriptionLoginStart start = client.startBrowserLogin(new BrowserSubscriptionLoginRequest("openai"), NOW);
        String state = queryValue(start.authorizationUri(), "state");

        assertThatThrownBy(() -> client.completeBrowserLoginCallback("browser-code", state, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access_token");
        assertThat(client.completeBrowserLoginCallback("browser-code", state, NOW.plusSeconds(2)).status())
                .isEqualTo(SubscriptionLoginStatus.FAILED);
    }

    @Test
    void tokenResponseRejectsInvalidExpiryAndTokenType() {
        FakeLoginTransport expiredTransport = new FakeLoginTransport()
                .enqueue(object().put("access_token", "token").put("expires_in", 0));
        OpenAiSubscriptionLoginClient expiredClient = new OpenAiSubscriptionLoginClient(options(), expiredTransport);
        SubscriptionLoginStart expiredStart = expiredClient.startBrowserLogin(new BrowserSubscriptionLoginRequest("openai"), NOW);

        assertThatThrownBy(() -> expiredClient.completeBrowserLoginCallback(
                "browser-code", queryValue(expiredStart.authorizationUri(), "state"), NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expires_in");

        FakeLoginTransport tokenTypeTransport = new FakeLoginTransport()
                .enqueue(object().put("access_token", "token").put("token_type", "MAC"));
        OpenAiSubscriptionLoginClient tokenTypeClient = new OpenAiSubscriptionLoginClient(options(), tokenTypeTransport);
        SubscriptionLoginStart tokenTypeStart = tokenTypeClient.startBrowserLogin(
                new BrowserSubscriptionLoginRequest("openai"), NOW);

        assertThatThrownBy(() -> tokenTypeClient.completeBrowserLoginCallback(
                "browser-code", queryValue(tokenTypeStart.authorizationUri(), "state"), NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token_type");
    }

    @Test
    void browserLoginErrorAndCancellationRemoveTemporaryFlowState() {
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(options(), new FakeLoginTransport());
        SubscriptionLoginStart errorStart = client.startBrowserLogin(new BrowserSubscriptionLoginRequest("openai"), NOW);
        String errorState = queryValue(errorStart.authorizationUri(), "state");

        SubscriptionLoginPollResult error = client.completeBrowserLoginErrorCallback(
                "access denied", Optional.of(errorState), NOW.plusSeconds(1));
        SubscriptionLoginPollResult afterError = client.completeBrowserLoginCallback(
                "browser-code", errorState, NOW.plusSeconds(2));

        SubscriptionLoginStart cancelledStart = client.startBrowserLogin(new BrowserSubscriptionLoginRequest("openai"), NOW);
        String cancelledState = queryValue(cancelledStart.authorizationUri(), "state");
        boolean cancelled = client.cancelLogin(cancelledStart.flowId(), NOW.plusSeconds(1));
        SubscriptionLoginPollResult afterCancellation = client.completeBrowserLoginCallback(
                "browser-code", cancelledState, NOW.plusSeconds(2));

        assertThat(error.status()).isEqualTo(SubscriptionLoginStatus.FAILED);
        assertThat(afterError.status()).isEqualTo(SubscriptionLoginStatus.FAILED);
        assertThat(cancelled).isTrue();
        assertThat(afterCancellation.status()).isEqualTo(SubscriptionLoginStatus.FAILED);
    }

    @Test
    void oneCallBrowserLoginTimesOutAndRemovesTemporaryFlowState() throws Exception {
        OpenAiSubscriptionLoginClientOptions timeoutOptions = OpenAiSubscriptionLoginClientOptions.builder(
                        "codex-client",
                        URI.create("https://auth.example.test/authorize"),
                        URI.create("https://auth.example.test/token"))
                .redirectUri(URI.create("http://localhost:1455/auth/callback"))
                .defaultBrowserFlowTtl(Duration.ofMillis(25))
                .build();
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(timeoutOptions, new FakeLoginTransport());
        List<URI> opened = new ArrayList<>();
        LoginService service = new DefaultLoginService(
                new InMemoryAuthCredentialStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                client,
                opened::add);

        BrowserSubscriptionLoginCallbackServer probe;
        try {
            probe = BrowserSubscriptionLoginCallbackServer.startDefaultBrowserCallback(service);
        } catch (IOException e) {
            Assumptions.assumeTrue(false, "local callback socket binding is not available: " + e.getMessage());
            return;
        }
        probe.close();

        assertThatThrownBy(service::loginOpenAiSubscription)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("timed out");
        String state = queryValue(opened.getFirst(), "state");
        assertThat(client.completeBrowserLoginCallback("browser-code", state, NOW.plusSeconds(1)).status())
                .isEqualTo(SubscriptionLoginStatus.FAILED);
    }

    @Test
    void oneCallBrowserLoginInterruptionCancelsAndRemovesTemporaryFlowState() throws Exception {
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(options(), new FakeLoginTransport());
        List<URI> opened = new ArrayList<>();
        CountDownLatch launched = new CountDownLatch(1);
        LoginService service = new DefaultLoginService(
                new InMemoryAuthCredentialStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                client,
                uri -> {
                    opened.add(uri);
                    launched.countDown();
                });

        BrowserSubscriptionLoginCallbackServer probe;
        try {
            probe = BrowserSubscriptionLoginCallbackServer.startDefaultBrowserCallback(service);
        } catch (IOException e) {
            Assumptions.assumeTrue(false, "local callback socket binding is not available: " + e.getMessage());
            return;
        }
        probe.close();

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                service.loginOpenAiSubscription();
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        thread.start();
        assertThat(launched.await(1, TimeUnit.SECONDS)).isTrue();
        thread.interrupt();
        thread.join(1_000);

        assertThat(thread.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(IOException.class).hasMessageContaining("cancelled");
        String state = queryValue(opened.getFirst(), "state");
        assertThat(client.completeBrowserLoginCallback("browser-code", state, NOW.plusSeconds(1)).status())
                .isEqualTo(SubscriptionLoginStatus.FAILED);
    }

    @Test
    void deviceCodeLoginStartsAndPollsPendingThenCompletedToken() {
        FakeLoginTransport transport = new FakeLoginTransport()
                .enqueue(object()
                        .put("device_code", "device-code")
                        .put("user_code", "ABCD-1234")
                        .put("verification_uri", "https://auth.openai.com/codex/device")
                        .put("verification_uri_complete", "https://auth.openai.com/codex/device?user_code=ABCD-1234")
                        .put("expires_in", 600)
                        .put("interval", 7))
                .enqueue(object().put("error", "authorization_pending"))
                .enqueue(object()
                        .put("access_token", "device-token")
                        .put("expires_in", 1800)
                        .put("scope", "openid profile")
                        .put("account_id", "acct-1"));
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(options(), transport);

        SubscriptionLoginStart start = client.startDeviceCodeLogin(new DeviceCodeSubscriptionLoginRequest("openai"), NOW);
        SubscriptionLoginPollResult pending = client.pollLogin(start.flowId(), NOW.plusSeconds(1));
        SubscriptionLoginPollResult completed = client.pollLogin(start.flowId(), NOW.plusSeconds(2));

        assertThat(start.mode()).isEqualTo(SubscriptionLoginMode.DEVICE_CODE);
        assertThat(start.authorizationUri()).isEqualTo(URI.create("https://auth.openai.com/codex/device?user_code=ABCD-1234"));
        assertThat(start.verificationUri()).contains(URI.create("https://auth.openai.com/codex/device"));
        assertThat(start.userCode()).contains("ABCD-1234");
        assertThat(start.expiresAt()).contains(NOW.plusSeconds(600));
        assertThat(pending.status()).isEqualTo(SubscriptionLoginStatus.PENDING);
        assertThat(pending.retryAfter()).contains(NOW.plusSeconds(8));
        assertThat(completed.status()).isEqualTo(SubscriptionLoginStatus.COMPLETED);
        assertThat(completed.completion().orElseThrow().accessToken()).isEqualTo("device-token");
        assertThat(completed.completion().orElseThrow().metadata()).containsEntry("accountId", "acct-1");
        assertThat(transport.requests()).extracting(Request::endpoint)
                .containsExactly(
                        URI.create("https://auth.example.test/device"),
                        URI.create("https://auth.example.test/token"),
                        URI.create("https://auth.example.test/token"));
        assertThat(transport.requests().get(1).form()).containsEntry("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        assertThat(transport.requests().get(1).form()).containsEntry("device_code", "device-code");
    }

    @Test
    void defaultLoginServicePollStoresCompletedSubscriptionSession() {
        FakeLoginTransport transport = new FakeLoginTransport()
                .enqueue(object()
                        .put("device_code", "device-code")
                        .put("user_code", "ABCD-1234")
                        .put("verification_uri", "https://auth.openai.com/codex/device")
                        .put("expires_in", 600))
                .enqueue(object()
                        .put("access_token", "device-token")
                        .put("expires_in", 1800)
                        .put("plan", "plus"));
        LoginService service = new DefaultLoginService(
                new InMemoryAuthCredentialStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OpenAiSubscriptionLoginClient(options(), transport));

        SubscriptionLoginStart start = service.startDeviceCodeSubscriptionLogin(new DeviceCodeSubscriptionLoginRequest("openai"));
        SubscriptionLoginPollResult result = service.pollSubscriptionLogin(start.flowId());

        assertThat(result.status()).isEqualTo(SubscriptionLoginStatus.COMPLETED);
        assertThat(service.status("openai").mode()).isEqualTo(AiAuthMode.CHATGPT_SUBSCRIPTION);
        assertThat(service.resolveAuth("openai").accessToken()).contains("device-token");
        assertThat(service.resolveAuth("openai").metadata()).containsEntry("plan", "plus");
    }

    @Test
    void defaultLoginServiceBrowserCallbackStoresCompletedSubscriptionSession() {
        FakeLoginTransport transport = new FakeLoginTransport()
                .enqueue(object()
                        .put("access_token", "browser-token")
                        .put("expires_in", 1800)
                        .put("plan", "plus"));
        LoginService service = new DefaultLoginService(
                new InMemoryAuthCredentialStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OpenAiSubscriptionLoginClient(options(), transport));

        SubscriptionLoginStart start = service.startBrowserSubscriptionLogin(new BrowserSubscriptionLoginRequest("openai"));
        SubscriptionLoginPollResult result = service.completeBrowserSubscriptionLoginCallback(
                "browser-code",
                queryValue(start.authorizationUri(), "state"));

        assertThat(result.status()).isEqualTo(SubscriptionLoginStatus.COMPLETED);
        assertThat(service.status("openai").mode()).isEqualTo(AiAuthMode.CHATGPT_SUBSCRIPTION);
        assertThat(service.resolveAuth("openai").accessToken()).contains("browser-token");
        assertThat(service.status("openai").metadata()).containsEntry("plan", "plus");
    }

    @Test
    void refreshLoginExchangesStoredRefreshTokenAndPreservesMetadata() {
        FakeLoginTransport transport = new FakeLoginTransport()
                .enqueue(object()
                        .put("access_token", "refreshed-token")
                        .put("expires_in", 3600)
                        .put("token_type", "Bearer"));
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(options(), transport);
        AuthSession expired = new AuthSession(
                "openai",
                AiAuthMode.CHATGPT_SUBSCRIPTION,
                AiResolvedAuth.chatGptSubscription(
                        "expired-token",
                        Optional.of("https://codex.openai.com/api"),
                        Optional.of("sdk-login"),
                        Optional.of(NOW.minusSeconds(1)),
                        Map.of("refreshToken", "refresh-token", "plan", "plus")),
                NOW.minusSeconds(3600));

        Optional<SubscriptionLoginCompletion> refreshed = client.refreshLogin(expired, NOW);

        assertThat(refreshed).isPresent();
        assertThat(refreshed.orElseThrow().accessToken()).isEqualTo("refreshed-token");
        assertThat(refreshed.orElseThrow().expiresAt()).contains(NOW.plusSeconds(3600));
        assertThat(refreshed.orElseThrow().baseUrl()).contains("https://codex.openai.com/api");
        assertThat(refreshed.orElseThrow().metadata()).containsEntry("refreshToken", "refresh-token");
        assertThat(refreshed.orElseThrow().metadata()).containsEntry("plan", "plus");
        assertThat(transport.requests()).hasSize(1);
        assertThat(transport.requests().getFirst().form()).containsEntry("grant_type", "refresh_token");
        assertThat(transport.requests().getFirst().form()).containsEntry("refresh_token", "refresh-token");
    }

    @Test
    void refreshLoginReturnsEmptyWhenTokenEndpointRejectsRefreshToken() {
        FakeLoginTransport transport = new FakeLoginTransport()
                .enqueue(object()
                        .put("error", "invalid_grant")
                        .put("error_description", "refresh token revoked"));
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(options(), transport);
        AuthSession expired = new AuthSession(
                "openai",
                AiAuthMode.CHATGPT_SUBSCRIPTION,
                AiResolvedAuth.chatGptSubscription(
                        "expired-token",
                        Optional.empty(),
                        Optional.of("sdk-login"),
                        Optional.of(NOW.minusSeconds(1)),
                        Map.of("refreshToken", "refresh-token")),
                NOW.minusSeconds(3600));

        assertThat(client.refreshLogin(expired, NOW)).isEmpty();
        assertThat(transport.requests().getFirst().form()).containsEntry("grant_type", "refresh_token");
    }

    @Test
    void refreshLoginRejectsBlankRotatedRefreshToken() {
        FakeLoginTransport transport = new FakeLoginTransport()
                .enqueue(object()
                        .put("access_token", "refreshed-token")
                        .put("refresh_token", " "));
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(options(), transport);
        AuthSession expired = new AuthSession(
                "openai",
                AiAuthMode.CHATGPT_SUBSCRIPTION,
                AiResolvedAuth.chatGptSubscription(
                        "expired-token",
                        Optional.empty(),
                        Optional.of("sdk-login"),
                        Optional.of(NOW.minusSeconds(1)),
                        Map.of("refreshToken", "previous-refresh-token")),
                NOW.minusSeconds(3600));

        assertThatThrownBy(() -> client.refreshLogin(expired, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refresh_token");
    }

    @Test
    void defaultLoginServiceRefreshesExpiredSubscriptionBeforeResolvingAuthAndStatus() {
        FakeLoginTransport transport = new FakeLoginTransport()
                .enqueue(object()
                        .put("access_token", "refreshed-token")
                        .put("expires_in", 3600)
                        .put("refresh_token", "rotated-refresh-token")
                        .put("plan_type", "team"));
        InMemoryAuthCredentialStore store = new InMemoryAuthCredentialStore();
        store.save(new AuthSession(
                "openai",
                AiAuthMode.CHATGPT_SUBSCRIPTION,
                AiResolvedAuth.chatGptSubscription(
                        "expired-token",
                        Optional.of("https://codex.openai.com/api"),
                        Optional.of("sdk-login"),
                        Optional.of(NOW.minusSeconds(1)),
                        Map.of("refreshToken", "refresh-token", "plan", "plus")),
                NOW.minusSeconds(3600)));
        LoginService service = new DefaultLoginService(
                store,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OpenAiSubscriptionLoginClient(options(), transport));

        AiResolvedAuth auth = service.resolveAuth("openai");
        AuthStatus status = service.status("openai");

        assertThat(auth.accessToken()).contains("refreshed-token");
        assertThat(auth.expiresAt()).contains(NOW.plusSeconds(3600));
        assertThat(auth.metadata()).containsEntry("refreshToken", "rotated-refresh-token");
        assertThat(auth.metadata()).containsEntry("plan", "team");
        assertThat(status.expired()).isFalse();
        assertThat(status.metadata()).containsEntry("plan", "team");
        assertThat(status.metadata()).containsEntry("refreshToken", "rotated-refresh-token");
        assertThat(transport.requests()).hasSize(1);
    }

    @Test
    void explicitRefreshAuthReturnsEmptyWhenRefreshTokenIsUnavailable() {
        InMemoryAuthCredentialStore store = new InMemoryAuthCredentialStore();
        store.save(new AuthSession(
                "openai",
                AiAuthMode.CHATGPT_SUBSCRIPTION,
                AiResolvedAuth.chatGptSubscription(
                        "expired-token",
                        Optional.empty(),
                        Optional.of("sdk-login"),
                        Optional.of(NOW.minusSeconds(1)),
                        Map.of("plan", "plus")),
                NOW.minusSeconds(3600)));
        LoginService service = new DefaultLoginService(
                store,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OpenAiSubscriptionLoginClient(options(), new FakeLoginTransport()));

        assertThat(service.refreshAuth("openai")).isEmpty();
        assertThat(service.resolveAuth("openai")).isEqualTo(AiResolvedAuth.none());
    }

    @Test
    void deviceCodePollHandlesSlowDownExpiredAndFailureResponses() {
        FakeLoginTransport slowDownTransport = new FakeLoginTransport()
                .enqueue(object()
                        .put("device_code", "device-code")
                        .put("user_code", "ABCD-1234")
                        .put("verification_uri", "https://auth.openai.com/codex/device")
                        .put("expires_in", 600)
                        .put("interval", 7))
                .enqueue(object().put("error", "slow_down"));
        OpenAiSubscriptionLoginClient slowDown = new OpenAiSubscriptionLoginClient(options(), slowDownTransport);
        SubscriptionLoginStart slowDownStart = slowDown.startDeviceCodeLogin(new DeviceCodeSubscriptionLoginRequest("openai"), NOW);

        SubscriptionLoginPollResult slowDownResult = slowDown.pollLogin(slowDownStart.flowId(), NOW.plusSeconds(1));

        assertThat(slowDownResult.status()).isEqualTo(SubscriptionLoginStatus.PENDING);
        assertThat(slowDownResult.retryAfter()).contains(NOW.plusSeconds(13));

        FakeLoginTransport expiredTransport = new FakeLoginTransport()
                .enqueue(object()
                        .put("device_code", "device-code")
                        .put("user_code", "ABCD-1234")
                        .put("verification_uri", "https://auth.openai.com/codex/device")
                        .put("expires_in", 600))
                .enqueue(object().put("error", "expired_token"));
        OpenAiSubscriptionLoginClient expired = new OpenAiSubscriptionLoginClient(options(), expiredTransport);
        SubscriptionLoginStart expiredStart = expired.startDeviceCodeLogin(new DeviceCodeSubscriptionLoginRequest("openai"), NOW);

        SubscriptionLoginPollResult expiredResult = expired.pollLogin(expiredStart.flowId(), NOW.plusSeconds(1));
        SubscriptionLoginPollResult secondExpiredPoll = expired.pollLogin(expiredStart.flowId(), NOW.plusSeconds(2));

        assertThat(expiredResult.status()).isEqualTo(SubscriptionLoginStatus.EXPIRED);
        assertThat(secondExpiredPoll.status()).isEqualTo(SubscriptionLoginStatus.FAILED);

        FakeLoginTransport failureTransport = new FakeLoginTransport()
                .enqueue(object()
                        .put("device_code", "device-code")
                        .put("user_code", "ABCD-1234")
                        .put("verification_uri", "https://auth.openai.com/codex/device")
                        .put("expires_in", 600))
                .enqueue(object()
                        .put("error", "access_denied")
                        .put("error_description", "user denied"));
        OpenAiSubscriptionLoginClient failure = new OpenAiSubscriptionLoginClient(options(), failureTransport);
        SubscriptionLoginStart failureStart = failure.startDeviceCodeLogin(new DeviceCodeSubscriptionLoginRequest("openai"), NOW);

        SubscriptionLoginPollResult failureResult = failure.pollLogin(failureStart.flowId(), NOW.plusSeconds(1));

        assertThat(failureResult.status()).isEqualTo(SubscriptionLoginStatus.FAILED);
        assertThat(failureResult.error()).contains("user denied");
    }

    @Test
    void callbackServerCompletesBrowserLoginAndStoresSubscriptionSession() throws Exception {
        FakeLoginTransport transport = new FakeLoginTransport()
                .enqueue(object()
                        .put("access_token", "browser-token")
                        .put("expires_in", 1800)
                        .put("plan", "plus"));
        LoginService service = new DefaultLoginService(
                new InMemoryAuthCredentialStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OpenAiSubscriptionLoginClient(optionsWithoutRedirect(), transport));

        BrowserSubscriptionLoginCallbackServer callbackServer;
        try {
            callbackServer = BrowserSubscriptionLoginCallbackServer.start(service);
        } catch (IOException e) {
            Assumptions.assumeTrue(false, "local callback socket binding is not available: " + e.getMessage());
            return;
        }
        try (callbackServer) {
            SubscriptionLoginStart start = service.startBrowserSubscriptionLogin(new BrowserSubscriptionLoginRequest(
                    "openai",
                    Optional.empty(),
                    Map.of(),
                    Optional.of(callbackServer.redirectUri())));
            String state = queryValue(start.authorizationUri(), "state");
            URI callbackUri = URI.create(callbackServer.redirectUri() + "?code=browser-code&state=" + state);

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder(callbackUri).GET().build(), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> duplicateResponse = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder(callbackUri).GET().build(), HttpResponse.BodyHandlers.ofString());
            SubscriptionLoginPollResult result = callbackServer.completion().join();

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("Login complete");
            assertThat(duplicateResponse.statusCode()).isEqualTo(409);
            assertThat(result.status()).isEqualTo(SubscriptionLoginStatus.COMPLETED);
            assertThat(service.status("openai").mode()).isEqualTo(AiAuthMode.CHATGPT_SUBSCRIPTION);
            assertThat(service.resolveAuth("openai").accessToken()).contains("browser-token");
            assertThat(transport.requests().getFirst().form()).containsEntry("code", "browser-code");
            assertThat(transport.requests().getFirst().form())
                    .containsEntry("redirect_uri", callbackServer.redirectUri().toString());
            assertThat(transport.requests()).hasSize(1);
        }
    }

    @Test
    void callbackServerHandlesOauthErrorsAndShutdownAsTerminalResults() throws Exception {
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(optionsWithoutRedirect(), new FakeLoginTransport());
        LoginService service = new DefaultLoginService(
                new InMemoryAuthCredentialStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                client);

        BrowserSubscriptionLoginCallbackServer callbackServer;
        try {
            callbackServer = BrowserSubscriptionLoginCallbackServer.start(service);
        } catch (IOException e) {
            Assumptions.assumeTrue(false, "local callback socket binding is not available: " + e.getMessage());
            return;
        }
        try (callbackServer) {
            SubscriptionLoginStart start = service.startBrowserSubscriptionLogin(new BrowserSubscriptionLoginRequest(
                    "openai", Optional.empty(), Map.of(), Optional.of(callbackServer.redirectUri())));
            String state = queryValue(start.authorizationUri(), "state");
            URI errorUri = URI.create(callbackServer.redirectUri()
                    + "?error=access_denied&error_description=user+denied&state=" + state);

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder(errorUri).GET().build(), HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(callbackServer.completion().join().status()).isEqualTo(SubscriptionLoginStatus.FAILED);
            assertThat(client.completeBrowserLoginCallback("browser-code", state, NOW.plusSeconds(1)).status())
                    .isEqualTo(SubscriptionLoginStatus.FAILED);
        }

        BrowserSubscriptionLoginCallbackServer shutdownServer;
        try {
            shutdownServer = BrowserSubscriptionLoginCallbackServer.start(service);
        } catch (IOException e) {
            Assumptions.assumeTrue(false, "local callback socket binding is not available: " + e.getMessage());
            return;
        }
        shutdownServer.close();
        assertThat(shutdownServer.completion().join().status()).isEqualTo(SubscriptionLoginStatus.FAILED);
    }

    @Test
    void deviceCodeLoginRequiresConfiguredEndpoint() {
        OpenAiSubscriptionLoginClientOptions options = OpenAiSubscriptionLoginClientOptions.builder(
                        "codex-client",
                        URI.create("https://auth.example.test/authorize"),
                        URI.create("https://auth.example.test/token"))
                .build();
        OpenAiSubscriptionLoginClient client = new OpenAiSubscriptionLoginClient(options, new FakeLoginTransport());

        assertThatThrownBy(() -> client.startDeviceCodeLogin(new DeviceCodeSubscriptionLoginRequest("openai"), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deviceAuthorizationEndpoint");
    }

    private static OpenAiSubscriptionLoginClientOptions options() {
        return OpenAiSubscriptionLoginClientOptions.builder(
                        "codex-client",
                        URI.create("https://auth.example.test/authorize"),
                        URI.create("https://auth.example.test/token"))
                .deviceAuthorizationEndpoint(URI.create("https://auth.example.test/device"))
                .redirectUri(URI.create("http://localhost:1455/auth/callback"))
                .baseUrl("https://codex.openai.com/api")
                .headers(Map.of("X-Test", "true"))
                .build();
    }

    private static OpenAiSubscriptionLoginClientOptions optionsWithoutRedirect() {
        return OpenAiSubscriptionLoginClientOptions.builder(
                        "codex-client",
                        URI.create("https://auth.example.test/authorize"),
                        URI.create("https://auth.example.test/token"))
                .deviceAuthorizationEndpoint(URI.create("https://auth.example.test/device"))
                .baseUrl("https://codex.openai.com/api")
                .build();
    }

    private static String queryValue(URI uri, String key) {
        for (String part : uri.getRawQuery().split("&")) {
            int separator = part.indexOf('=');
            String name = separator < 0 ? part : part.substring(0, separator);
            if (URLDecoder.decode(name, StandardCharsets.UTF_8).equals(key)) {
                String value = separator < 0 ? "" : part.substring(separator + 1);
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("missing query value: " + key);
    }

    private static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    private record Request(URI endpoint, Map<String, String> form, Map<String, String> headers) {
        private Request {
            form = Map.copyOf(form);
            headers = Map.copyOf(headers);
        }
    }

    private static final class FakeLoginTransport implements OpenAiSubscriptionLoginHttpTransport {
        private final Queue<JsonNode> responses = new ArrayDeque<>();
        private final List<Request> requests = new ArrayList<>();

        private FakeLoginTransport enqueue(JsonNode response) {
            responses.add(response);
            return this;
        }

        @Override
        public JsonNode postForm(URI endpoint, Map<String, String> form, Map<String, String> headers) {
            requests.add(new Request(endpoint, form, headers));
            if (responses.isEmpty()) {
                throw new AssertionError("no fake response queued");
            }
            return responses.remove();
        }

        private List<Request> requests() {
            return List.copyOf(requests);
        }
    }
}
