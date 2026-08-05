package com.agent4j.coding.sdk;

import com.agent4j.ai.AiAuthMode;
import com.agent4j.ai.AiResolvedAuth;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    void runtimeServicesExposeDefaultAndConfiguredLoginService() {
        LoginService configured = service();

        CodingAgentRuntimeServices services = CodingAgentRuntimeServices.builder()
                .loginService(configured)
                .build();

        assertThat(CodingAgentRuntimeServices.defaults().loginService()).isNotNull();
        assertThat(services.loginService()).isSameAs(configured);
        assertThat(new CodingAgentSessionRuntime(services).loginService()).isSameAs(configured);
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
    }

    private LoginService service() {
        return new DefaultLoginService(new InMemoryAuthCredentialStore(), clock);
    }
}
