package com.agent4j.coding.sdk;

import com.agent4j.ai.AiAuthMode;
import com.agent4j.ai.AiResolvedAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistentAuthCredentialStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsAndReloadsApiKeySessionFromUserScopedFile() throws Exception {
        Path credentialFile = tempDir.resolve("home").resolve(".pi").resolve("agent").resolve("auth.json");
        PersistentAuthCredentialStore store = new PersistentAuthCredentialStore(credentialFile);
        AuthSession session = new AuthSession(
                "openai",
                AiAuthMode.API_KEY,
                AiResolvedAuth.apiKey(
                        "sk-test",
                        Optional.of("https://api.example.test"),
                        Optional.of("sdk-login")),
                Instant.parse("2026-08-05T12:00:00Z"));

        store.save(session);

        PersistentAuthCredentialStore reloaded = new PersistentAuthCredentialStore(credentialFile);
        AuthSession found = reloaded.find("openai").orElseThrow();
        assertThat(found.providerId()).isEqualTo("openai");
        assertThat(found.mode()).isEqualTo(AiAuthMode.API_KEY);
        assertThat(found.authenticatedAt()).isEqualTo(Instant.parse("2026-08-05T12:00:00Z"));
        assertThat(found.auth().apiKey()).contains("sk-test");
        assertThat(found.auth().baseUrl()).contains("https://api.example.test");
        assertThat(found.auth().source()).contains("sdk-login");
        assertThat(credentialFile).exists();
    }

    @Test
    void persistsAndReloadsSubscriptionSessionMetadata() {
        Path credentialFile = tempDir.resolve("home").resolve(".pi").resolve("agent").resolve("auth.json");
        PersistentAuthCredentialStore store = new PersistentAuthCredentialStore(credentialFile);
        Instant expiresAt = Instant.parse("2026-08-05T13:00:00Z");
        AuthSession session = new AuthSession(
                "openai",
                AiAuthMode.CHATGPT_SUBSCRIPTION,
                AiResolvedAuth.chatGptSubscription(
                        "subscription-token",
                        Optional.of("https://codex.openai.com/api"),
                        Optional.of("sdk-login"),
                        Optional.of(expiresAt),
                        Map.of("plan", "plus", "accountId", "acct-1", "refreshToken", "refresh-token")),
                Instant.parse("2026-08-05T12:00:00Z"));

        store.save(session);

        AuthSession found = new PersistentAuthCredentialStore(credentialFile).find("openai").orElseThrow();
        assertThat(found.mode()).isEqualTo(AiAuthMode.CHATGPT_SUBSCRIPTION);
        assertThat(found.auth().accessToken()).contains("subscription-token");
        assertThat(found.auth().baseUrl()).contains("https://codex.openai.com/api");
        assertThat(found.auth().expiresAt()).contains(expiresAt);
        assertThat(found.auth().metadata()).containsEntry("plan", "plus");
        assertThat(found.auth().metadata()).containsEntry("accountId", "acct-1");
        assertThat(found.auth().metadata()).containsEntry("refreshToken", "refresh-token");
    }

    @Test
    void deleteRemovesPersistedSession() {
        Path credentialFile = tempDir.resolve("home").resolve(".pi").resolve("agent").resolve("auth.json");
        PersistentAuthCredentialStore store = new PersistentAuthCredentialStore(credentialFile);
        store.save(new AuthSession(
                "anthropic",
                AiAuthMode.ACCESS_TOKEN,
                AiResolvedAuth.accessToken("token", Optional.empty(), Optional.of("sdk-login"), Optional.empty(), Map.of()),
                Instant.parse("2026-08-05T12:00:00Z")));

        assertThat(store.delete("anthropic")).isTrue();
        assertThat(new PersistentAuthCredentialStore(credentialFile).find("anthropic")).isEmpty();
        assertThat(store.delete("anthropic")).isFalse();
    }

    @Test
    void defaultStorePathIsUserScopedOutsideProject() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("project");
        Files.createDirectories(cwd);
        PersistentAuthCredentialStore store = PersistentAuthCredentialStore.forHome(home);

        store.save(new AuthSession(
                "openai",
                AiAuthMode.API_KEY,
                AiResolvedAuth.apiKey("sk-test", Optional.empty(), Optional.of("sdk-login")),
                Instant.parse("2026-08-05T12:00:00Z")));

        assertThat(store.credentialFile()).isEqualTo(home.resolve(".pi/agent/auth.json").toAbsolutePath().normalize());
        assertThat(store.credentialFile()).exists();
        assertThat(cwd.resolve(".pi/agent/auth.json")).doesNotExist();
        assertThat(Files.readString(store.credentialFile())).contains("sk-test");
    }

    @Test
    void rejectsMalformedCredentialFile() throws Exception {
        Path credentialFile = tempDir.resolve("home").resolve(".pi").resolve("agent").resolve("auth.json");
        Files.createDirectories(credentialFile.getParent());
        Files.writeString(credentialFile, "{\"sessions\":{\"openai\":{\"mode\":\"api-key\"}}}");

        PersistentAuthCredentialStore store = new PersistentAuthCredentialStore(credentialFile);

        assertThatThrownBy(() -> store.find("openai"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authenticatedAt");
    }

    @Test
    void restrictsCredentialFilePermissionsWhenPosixIsAvailable() throws Exception {
        Path credentialFile = tempDir.resolve("home").resolve(".pi").resolve("agent").resolve("auth.json");
        PersistentAuthCredentialStore store = new PersistentAuthCredentialStore(credentialFile);

        store.save(new AuthSession(
                "openai",
                AiAuthMode.API_KEY,
                AiResolvedAuth.apiKey("sk-test", Optional.empty(), Optional.of("sdk-login")),
                Instant.parse("2026-08-05T12:00:00Z")));

        if (Files.getFileStore(credentialFile).supportsFileAttributeView("posix")) {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(credentialFile);
            assertThat(permissions).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        }
    }
}
