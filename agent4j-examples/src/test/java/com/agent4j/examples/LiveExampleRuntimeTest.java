package com.agent4j.examples;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveExampleRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void validatesCredentialsWithoutPersistingThemAndCleansDefaultDirectories() throws Exception {
        LiveExampleRuntime runtime = LiveExampleRuntime.open(Map.of(
                LiveExampleRuntime.OPENAI_API_KEY, "test-key-must-not-appear",
                LiveExampleRuntime.OPENAI_MODEL, "gpt-test"));
        Path workspace = runtime.workspace();
        Path sessions = runtime.sessionDirectory();

        assertThat(runtime.model().modelId()).isEqualTo("gpt-test");
        assertThat(runtime.temporaryWorkspace()).isTrue();
        assertThat(runtime.temporarySessionDirectory()).isTrue();
        assertThat(workspace).exists();
        assertThat(sessions).exists();

        runtime.close();

        assertThat(workspace).doesNotExist();
        assertThat(sessions).doesNotExist();
    }

    @Test
    void retainsExplicitWorkspaceAndSessionDirectories() throws Exception {
        Path workspace = temporaryDirectory.resolve("workspace");
        Path sessions = temporaryDirectory.resolve("sessions");
        LiveExampleRuntime runtime = LiveExampleRuntime.open(Map.of(
                LiveExampleRuntime.OPENAI_API_KEY, "test-key",
                LiveExampleRuntime.OPENAI_MODEL, "gpt-test",
                LiveExampleRuntime.WORKSPACE, workspace.toString(),
                LiveExampleRuntime.SESSION_DIRECTORY, sessions.toString(),
                LiveExampleRuntime.MAX_OUTPUT_TOKENS, "128",
                LiveExampleRuntime.MAX_TOOL_ROUNDS, "2"));

        runtime.close();

        assertThat(workspace).exists();
        assertThat(sessions).exists();
    }

    @Test
    void configuresAnOptionalOpenAiCompatibleBaseUrlInMemory() throws Exception {
        try (LiveExampleRuntime runtime = LiveExampleRuntime.open(Map.of(
                LiveExampleRuntime.OPENAI_API_KEY, "test-key",
                LiveExampleRuntime.OPENAI_BASE_URL, "https://openrouter.example/api/v1/ ",
                LiveExampleRuntime.OPENAI_MODEL, "openrouter/free"))) {
            assertThat(runtime.baseUrl()).contains("https://openrouter.example/api/v1/");
            assertThat(runtime.runtime().loginService().resolveAuth("openai").baseUrl())
                    .contains("https://openrouter.example/api/v1/");
        }
    }

    @Test
    void rejectsMissingCredentialsAndUnsafeSessionNames() throws Exception {
        assertThatThrownBy(() -> LiveExampleRuntime.open(Map.of(
                LiveExampleRuntime.OPENAI_MODEL, "gpt-test")))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> assertThat(error.getMessage())
                        .contains(LiveExampleRuntime.OPENAI_API_KEY)
                        .doesNotContain("test-key"));

        try (LiveExampleRuntime runtime = LiveExampleRuntime.open(Map.of(
                LiveExampleRuntime.OPENAI_API_KEY, "test-key",
                LiveExampleRuntime.OPENAI_MODEL, "gpt-test"))) {
            assertThatThrownBy(() -> runtime.createSession("../session.jsonl"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("single .jsonl");
        }
    }
}
