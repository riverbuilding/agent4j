package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiResolvedAuth;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interactive test for a real ChatGPT subscription login. It is excluded from
 * normal test discovery and needs explicit environment configuration.
 */
@Tag("live-openai")
@EnabledIfSystemProperty(named = "agent4j.liveOpenAi", matches = "true")
@EnabledIfEnvironmentVariable(named = "AGENT4J_LIVE_OPENAI", matches = "true")
class OpenAiSubscriptionLiveIT {
    @Test
    void browserLoginPersistsRefreshesResolvesAuthAndPrompts(
            @TempDir Path temporaryDirectory
    ) throws Exception {
        Path credentialFile = Path.of(requiredEnvironment("AGENT4J_LIVE_OPENAI_AUTH_FILE"));
        AiModelReference model = new AiModelReference("openai", requiredEnvironment("AGENT4J_LIVE_OPENAI_MODEL"));
        PersistentAuthCredentialStore credentialStore = new PersistentAuthCredentialStore(credentialFile);
        CodingAgentRuntime runtime = CodingAgentRuntime.builder().openAi(
                OpenAiCodingRuntimeOptions.builder(model)
                        .credentialStore(credentialStore)
                        .build()).build();
        LoginService loginService = runtime.loginService();

        AuthStatus browserLogin = loginService.loginOpenAiSubscription();
        assertThat(browserLogin.authenticated()).isTrue();
        assertThat(browserLogin.providerId()).isEqualTo("openai");

        AuthSession persisted = new PersistentAuthCredentialStore(credentialFile)
                .find("openai")
                .orElseThrow();
        assertThat(persisted.auth().accessToken()).isPresent();
        assertThat(persisted.auth().metadata()).containsKey("refreshToken");

        AuthSession refreshed = loginService.refreshAuth("openai").orElseThrow();
        assertThat(refreshed.auth().accessToken()).isPresent();
        assertThat(refreshed.auth().metadata()).containsKey("refreshToken");
        AuthSession reloadedAfterRefresh = new PersistentAuthCredentialStore(credentialFile)
                .find("openai")
                .orElseThrow();
        assertThat(reloadedAfterRefresh.auth().accessToken()).isEqualTo(refreshed.auth().accessToken());
        assertThat(reloadedAfterRefresh.auth().metadata()).containsKey("refreshToken");

        AiResolvedAuth resolvedAuth = loginService.resolveAuth("openai");
        assertThat(resolvedAuth.accessToken()).isPresent();
        assertThat(resolvedAuth.baseUrl()).isPresent();

        AgentSession session = runtime.createSession(new CreateSessionRequest(
                temporaryDirectory.resolve("live-openai-session.jsonl"),
                temporaryDirectory));
        PromptResult prompt = session.prompt(new PromptRequest("Reply with exactly: live integration ok."));

        assertThat(prompt.loopResult().assistantMessages()).isNotEmpty();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("required environment variable is missing: " + name);
        }
        return value;
    }
}
