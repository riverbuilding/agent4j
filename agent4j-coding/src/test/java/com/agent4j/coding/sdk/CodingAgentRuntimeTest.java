package com.agent4j.coding.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodingAgentRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void opensAnOpenAiRuntimeAndCreatesTypedSessions() throws Exception {
        CodingAgentRuntime runtime = CodingAgentRuntime.openAi(OpenAiCodingAgentConfig.builder("test-key", "gpt-test")
                .maxOutputTokens(256)
                .build());

        CodingAgentSession session = runtime.createSession(
                temporaryDirectory.resolve("session.jsonl"), temporaryDirectory);

        assertThat(runtime.defaultModel().providerId()).isEqualTo("openai");
        assertThat(runtime.defaultModel().modelId()).isEqualTo("gpt-test");
        assertThat(session.info().sessionFile()).isEqualTo(temporaryDirectory.resolve("session.jsonl"));
        assertThat(session.info().cwd()).isEqualTo(temporaryDirectory);
    }

    @Test
    void rejectsInvalidOpenAiConfiguration() {
        assertThatThrownBy(() -> OpenAiCodingAgentConfig.builder("", "gpt-test").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
        assertThatThrownBy(() -> OpenAiCodingAgentConfig.builder("test-key", "gpt-test")
                .maxOutputTokens(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxOutputTokens");
    }
}
