package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModelReference;
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
        CodingAgentSession resumed = runtime.resumeSession(session.sessionFile());

        assertThat(runtime.defaultModel().providerId()).isEqualTo("openai");
        assertThat(runtime.defaultModel().modelId()).isEqualTo("gpt-test");
        assertThat(session.info().sessionFile()).isEqualTo(temporaryDirectory.resolve("session.jsonl"));
        assertThat(session.info().cwd()).isEqualTo(temporaryDirectory);
        assertThat(resumed.id()).isEqualTo(session.id());
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
