package com.agent4j.coding.sdk;

import com.agent4j.ai.AiAuthMode;
import com.agent4j.ai.AiResolvedAuth;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void loginApiKeyStoresSessionAndResolvesAuth() {
        LoginService service = service();

        AuthSession session = service.loginApiKey(new ApiKeyLoginRequest(
                "openai",
                "sk-test",
                Optional.of("https://api.example.test")));

        assertThat(session.providerId()).isEqualTo("openai");
        assertThat(session.mode()).isEqualTo(AiAuthMode.API_KEY);
        assertThat(session.authenticatedAt()).isEqualTo(Instant.parse("2026-08-05T12:00:00Z"));
        assertThat(session.auth().apiKey()).contains("sk-test");
        assertThat(session.auth().baseUrl()).contains("https://api.example.test");
        assertThat(session.auth().source()).contains("sdk-login");

        AuthStatus status = service.status("openai");
        assertThat(status.authenticated()).isTrue();
        assertThat(status.expired()).isFalse();
        assertThat(status.mode()).isEqualTo(AiAuthMode.API_KEY);
        assertThat(service.resolveAuth("openai")).isEqualTo(session.auth());
    }

    @Test
    void loginAccessTokenStoresExpiryAndMetadata() {
        LoginService service = service();
        Instant expiresAt = Instant.parse("2026-08-05T13:00:00Z");

        AuthSession session = service.loginAccessToken(new AccessTokenLoginRequest(
                "openai",
                "oauth-token",
                Optional.empty(),
                Optional.of(expiresAt),
                Map.of("plan", "plus")));

        assertThat(session.mode()).isEqualTo(AiAuthMode.ACCESS_TOKEN);
        assertThat(session.expiresAt()).contains(expiresAt);
        assertThat(session.auth().accessToken()).contains("oauth-token");
        assertThat(session.auth().metadata()).containsEntry("plan", "plus");
        assertThat(service.status("openai").expiresAt()).contains(expiresAt);
    }

    @Test
    void expiredTokenStatusRemainsAuthenticatedButResolveReturnsNone() {
        LoginService service = service();
        service.loginAccessToken(new AccessTokenLoginRequest(
                "openai",
                "expired-token",
                Optional.empty(),
                Optional.of(Instant.parse("2026-08-05T11:59:59Z")),
                Map.of()));

        AuthStatus status = service.status("openai");

        assertThat(status.authenticated()).isTrue();
        assertThat(status.expired()).isTrue();
        assertThat(service.resolveAuth("openai")).isEqualTo(AiResolvedAuth.none());
    }

    @Test
    void logoutClearsStoredSession() {
        LoginService service = service();
        service.loginApiKey(new ApiKeyLoginRequest("anthropic", "sk-ant"));

        assertThat(service.logout("anthropic")).isTrue();
        assertThat(service.logout("anthropic")).isFalse();
        assertThat(service.status("anthropic").authenticated()).isFalse();
        assertThat(service.resolveAuth("anthropic")).isEqualTo(AiResolvedAuth.none());
    }

    @Test
    void subscriptionBrowserLoginShapeStartsAndCompletesChatGptSubscriptionSession() {
        FakeSubscriptionLoginClient client = new FakeSubscriptionLoginClient();
        LoginService service = new DefaultLoginService(new InMemoryAuthCredentialStore(), clock, client);

        SubscriptionLoginStart start = service.startBrowserSubscriptionLogin(new BrowserSubscriptionLoginRequest(
                "openai",
                Optional.of("https://chatgpt.example.test"),
                Map.of("audience", "codex")));
        AuthSession session = service.completeSubscriptionLogin(new SubscriptionLoginCompletion(
                "openai",
                start.flowId(),
                "subscription-token",
                Optional.of("https://chatgpt.example.test"),
                Optional.of(Instant.parse("2026-08-05T13:00:00Z")),
                Map.of("plan", "plus")));

        assertThat(start.mode()).isEqualTo(SubscriptionLoginMode.BROWSER);
        assertThat(start.authorizationUri()).isEqualTo(URI.create("https://login.example.test/browser/fake-flow-1"));
        assertThat(start.verificationUri()).isEmpty();
        assertThat(start.userCode()).isEmpty();
        assertThat(client.browserRequests()).hasSize(1);
        assertThat(session.mode()).isEqualTo(AiAuthMode.CHATGPT_SUBSCRIPTION);
        assertThat(session.auth().accessToken()).contains("subscription-token");
        assertThat(session.auth().baseUrl()).contains("https://chatgpt.example.test");
        assertThat(session.auth().metadata()).containsEntry("plan", "plus");
        assertThat(service.resolveAuth("openai")).isEqualTo(session.auth());
        assertThat(service.status("openai").mode()).isEqualTo(AiAuthMode.CHATGPT_SUBSCRIPTION);
    }

    @Test
    void subscriptionDeviceCodeLoginShapeExposesVerificationAndUserCode() {
        FakeSubscriptionLoginClient client = new FakeSubscriptionLoginClient();
        LoginService service = new DefaultLoginService(new InMemoryAuthCredentialStore(), clock, client);

        SubscriptionLoginStart start = service.startDeviceCodeSubscriptionLogin(
                new DeviceCodeSubscriptionLoginRequest("openai"));

        assertThat(start.mode()).isEqualTo(SubscriptionLoginMode.DEVICE_CODE);
        assertThat(start.authorizationUri()).isEqualTo(URI.create("https://login.example.test/device/fake-flow-1"));
        assertThat(start.verificationUri()).contains(URI.create("https://login.example.test/activate"));
        assertThat(start.userCode()).contains("CODE-1");
        assertThat(start.expiresAt()).contains(Instant.parse("2026-08-05T12:05:00Z"));
        assertThat(client.deviceRequests()).hasSize(1);
    }

    @Test
    void defaultSubscriptionLoginClientFailsClearlyUntilOauthTransportIsConfigured() {
        LoginService service = service();

        assertThatThrownBy(() -> service.startBrowserSubscriptionLogin(new BrowserSubscriptionLoginRequest("openai")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("browser login");
        assertThatThrownBy(() -> service.startDeviceCodeSubscriptionLogin(new DeviceCodeSubscriptionLoginRequest("openai")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("device-code login");
    }

    @Test
    void runtimeExposesDefaultAndConfiguredLoginService() {
        LoginService configured = service();

        CodingAgentRuntime runtime = CodingAgentRuntime.builder()
                .loginService(configured)
                .build();

        assertThat(new CodingAgentRuntime().loginService()).isNotNull();
        assertThat(runtime.loginService()).isSameAs(configured);
    }

    @Test
    void loginRequestsRejectBlankRequiredFields() {
        assertThatThrownBy(() -> new ApiKeyLoginRequest(" ", "sk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerId");
        assertThatThrownBy(() -> new ApiKeyLoginRequest("openai", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
        assertThatThrownBy(() -> new AccessTokenLoginRequest("openai", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessToken");
        assertThatThrownBy(() -> new BrowserSubscriptionLoginRequest(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerId");
        assertThatThrownBy(() -> new DeviceCodeSubscriptionLoginRequest(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerId");
        assertThatThrownBy(() -> new SubscriptionLoginCompletion(
                        "openai",
                        "flow",
                        " ",
                        Optional.empty(),
                        Optional.empty(),
                        Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessToken");
    }

    private LoginService service() {
        return new DefaultLoginService(new InMemoryAuthCredentialStore(), clock);
    }

    private static final class FakeSubscriptionLoginClient implements SubscriptionLoginClient {
        private final List<BrowserSubscriptionLoginRequest> browserRequests = new ArrayList<>();
        private final List<DeviceCodeSubscriptionLoginRequest> deviceRequests = new ArrayList<>();

        @Override
        public SubscriptionLoginStart startBrowserLogin(BrowserSubscriptionLoginRequest request, Instant now) {
            browserRequests.add(request);
            String flowId = "fake-flow-" + browserRequests.size();
            return new SubscriptionLoginStart(
                    request.providerId(),
                    SubscriptionLoginMode.BROWSER,
                    flowId,
                    URI.create("https://login.example.test/browser/" + flowId),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(now.plusSeconds(300)),
                    request.metadata());
        }

        @Override
        public SubscriptionLoginStart startDeviceCodeLogin(DeviceCodeSubscriptionLoginRequest request, Instant now) {
            deviceRequests.add(request);
            String flowId = "fake-flow-" + deviceRequests.size();
            return new SubscriptionLoginStart(
                    request.providerId(),
                    SubscriptionLoginMode.DEVICE_CODE,
                    flowId,
                    URI.create("https://login.example.test/device/" + flowId),
                    Optional.of(URI.create("https://login.example.test/activate")),
                    Optional.of("CODE-" + deviceRequests.size()),
                    Optional.of(now.plusSeconds(300)),
                    request.metadata());
        }

        private List<BrowserSubscriptionLoginRequest> browserRequests() {
            return List.copyOf(browserRequests);
        }

        private List<DeviceCodeSubscriptionLoginRequest> deviceRequests() {
            return List.copyOf(deviceRequests);
        }
    }
}
