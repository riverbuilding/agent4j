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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

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
                    Map.of("redirectUri", callbackServer.redirectUri().toString())));
            String state = queryValue(start.authorizationUri(), "state");
            URI callbackUri = URI.create(callbackServer.redirectUri() + "?code=browser-code&state=" + state);

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder(callbackUri).GET().build(), HttpResponse.BodyHandlers.ofString());
            SubscriptionLoginPollResult result = callbackServer.completion().join();

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("Login complete");
            assertThat(result.status()).isEqualTo(SubscriptionLoginStatus.COMPLETED);
            assertThat(service.status("openai").mode()).isEqualTo(AiAuthMode.CHATGPT_SUBSCRIPTION);
            assertThat(service.resolveAuth("openai").accessToken()).contains("browser-token");
            assertThat(transport.requests().getFirst().form()).containsEntry("code", "browser-code");
            assertThat(transport.requests().getFirst().form())
                    .containsEntry("redirect_uri", callbackServer.redirectUri().toString());
        }
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
