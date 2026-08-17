package com.agent4j.examples;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.sdk.CodingAgentRuntime;
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
    void translatesConfigurationAndLetsTheRuntimeCleanOwnedDirectories() throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open(Map.of(
                LiveExampleConfiguration.API_KEY, "test-key-must-not-appear",
                LiveExampleConfiguration.MODEL, "gpt-5"));
        Path workspace = configuration.workspace();
        Path sessions = configuration.sessionDirectory();

        assertThat(configuration.model()).isEqualTo("gpt-5");
        assertThat(configuration.temporaryWorkspace()).isTrue();
        assertThat(configuration.temporarySessionDirectory()).isTrue();
        assertThat(workspace).doesNotExist();
        assertThat(sessions).doesNotExist();

        CodingAgentRuntime runtime = CodingAgentRuntime.create(configuration.toCodingAgentConfig());
        assertThat(workspace).exists();
        assertThat(sessions).exists();
        runtime.cleanupOwnedFiles();

        assertThat(workspace).doesNotExist();
        assertThat(sessions).doesNotExist();
    }

    @Test
    void retainsExplicitWorkspaceAndSessionDirectories() throws Exception {
        Path workspace = temporaryDirectory.resolve("workspace");
        Path sessions = temporaryDirectory.resolve("sessions");
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open(Map.of(
                LiveExampleConfiguration.API_KEY, "test-key",
                LiveExampleConfiguration.MODEL, "gpt-5",
                LiveExampleConfiguration.WORKSPACE, workspace.toString(),
                LiveExampleConfiguration.SESSION_DIRECTORY, sessions.toString(),
                LiveExampleConfiguration.MAX_OUTPUT_TOKENS, "128",
                LiveExampleConfiguration.MAX_TOOL_ROUNDS, "2"));

        CodingAgentRuntime runtime = CodingAgentRuntime.create(configuration.toCodingAgentConfig());
        runtime.cleanupOwnedFiles();

        assertThat(workspace).exists();
        assertThat(sessions).exists();
    }

    @Test
    void readsAnOptionalOpenAiCompatibleBaseUrl() throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open(Map.of(
                LiveExampleConfiguration.API_KEY, "test-key",
                LiveExampleConfiguration.BASE_URL, "https://openrouter.example/api/v1/ ",
                LiveExampleConfiguration.MODEL, "openai/openrouter/free"));
        assertThat(configuration.baseUrl()).contains("https://openrouter.example/api/v1/");
        CodingAgentRuntime runtime = CodingAgentRuntime.create(configuration.toCodingAgentConfig());
        try (runtime) {
            assertThat(runtime.defaultModel().displayName()).isEqualTo("openai/openrouter/free");
        } finally {
            runtime.cleanupOwnedFiles();
        }
    }

    @Test
    void registersTheOptionalSwitchModelForPromptOverrides() throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open(Map.of(
                LiveExampleConfiguration.API_KEY, "test-key",
                LiveExampleConfiguration.MODEL, "openai/openrouter/free",
                LiveExampleConfiguration.SWITCH_MODEL, "openai/meta-llama/llama-3.2-3b-instruct:free"));

        CodingAgentRuntime runtime = CodingAgentRuntime.create(configuration.toCodingAgentConfig());
        try (runtime) {
            assertThat(configuration.requireSwitchModel())
                    .isEqualTo(new AiModelReference("openai", "meta-llama/llama-3.2-3b-instruct:free"));
            assertThat(runtime.optionalProviderRegistry().orElseThrow().resolve(configuration.requireSwitchModel()))
                    .isPresent();
        } finally {
            runtime.cleanupOwnedFiles();
        }
    }

    @Test
    void rejectsMissingCredentialsAndUnsafeSessionNames() throws Exception {
        assertThatThrownBy(() -> LiveExampleConfiguration.open(Map.of(
                LiveExampleConfiguration.MODEL, "gpt-5")))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> assertThat(error.getMessage())
                        .contains(LiveExampleConfiguration.API_KEY)
                        .doesNotContain("test-key"));

        LiveExampleConfiguration configuration = LiveExampleConfiguration.open(Map.of(
                LiveExampleConfiguration.API_KEY, "test-key",
                LiveExampleConfiguration.MODEL, "gpt-5"));
        CodingAgentRuntime runtime = CodingAgentRuntime.create(configuration.toCodingAgentConfig());
        try (runtime) {
            assertThatThrownBy(() -> runtime.sessionFile("../session.jsonl"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("single .jsonl");
        } finally {
            runtime.cleanupOwnedFiles();
        }
    }
}
