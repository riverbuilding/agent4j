package com.agent4j.examples;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveExampleConfigurationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsConfigurationAndCleansDefaultDirectories() throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open(Map.of(
                LiveExampleConfiguration.OPENAI_API_KEY, "test-key-must-not-appear",
                LiveExampleConfiguration.OPENAI_MODEL, "gpt-test"));
        Path workspace = configuration.workspace();
        Path sessions = configuration.sessionDirectory();

        assertThat(configuration.model()).isEqualTo("gpt-test");
        assertThat(configuration.temporaryWorkspace()).isTrue();
        assertThat(configuration.temporarySessionDirectory()).isTrue();
        assertThat(workspace).exists();
        assertThat(sessions).exists();

        configuration.close();

        assertThat(workspace).doesNotExist();
        assertThat(sessions).doesNotExist();
    }

    @Test
    void retainsExplicitWorkspaceAndSessionDirectories() throws Exception {
        Path workspace = temporaryDirectory.resolve("workspace");
        Path sessions = temporaryDirectory.resolve("sessions");
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open(Map.of(
                LiveExampleConfiguration.OPENAI_API_KEY, "test-key",
                LiveExampleConfiguration.OPENAI_MODEL, "gpt-test",
                LiveExampleConfiguration.WORKSPACE, workspace.toString(),
                LiveExampleConfiguration.SESSION_DIRECTORY, sessions.toString(),
                LiveExampleConfiguration.MAX_OUTPUT_TOKENS, "128",
                LiveExampleConfiguration.MAX_TOOL_ROUNDS, "2"));

        configuration.close();

        assertThat(workspace).exists();
        assertThat(sessions).exists();
    }

    @Test
    void readsAnOptionalOpenAiCompatibleBaseUrl() throws Exception {
        try (LiveExampleConfiguration configuration = LiveExampleConfiguration.open(Map.of(
                LiveExampleConfiguration.OPENAI_API_KEY, "test-key",
                LiveExampleConfiguration.OPENAI_BASE_URL, "https://openrouter.example/api/v1/ ",
                LiveExampleConfiguration.OPENAI_MODEL, "openrouter/free"))) {
            assertThat(configuration.baseUrl()).contains("https://openrouter.example/api/v1/");
            assertThat(configuration.openAiConfig().baseUrl())
                    .contains("https://openrouter.example/api/v1/");
        }
    }

    @Test
    void rejectsMissingCredentialsAndUnsafeSessionNames() throws Exception {
        assertThatThrownBy(() -> LiveExampleConfiguration.open(Map.of(
                LiveExampleConfiguration.OPENAI_MODEL, "gpt-test")))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> assertThat(error.getMessage())
                        .contains(LiveExampleConfiguration.OPENAI_API_KEY)
                        .doesNotContain("test-key"));

        try (LiveExampleConfiguration configuration = LiveExampleConfiguration.open(Map.of(
                LiveExampleConfiguration.OPENAI_API_KEY, "test-key",
                LiveExampleConfiguration.OPENAI_MODEL, "gpt-test"))) {
            assertThatThrownBy(() -> configuration.sessionFile("../session.jsonl"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("single .jsonl");
        }
    }
}
