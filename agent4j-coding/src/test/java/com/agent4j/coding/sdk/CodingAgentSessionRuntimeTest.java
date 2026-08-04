package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.session.SessionEntry;
import com.agent4j.coding.session.SessionEntryType;
import com.agent4j.coding.session.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodingAgentSessionRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    void createSessionCreatesJsonlSessionAndReturnsHandle() throws Exception {
        Path sessionFile = tempDir.resolve("session.jsonl");
        CodingAgentSessionRuntime runtime = new CodingAgentSessionRuntime();

        AgentSession session = runtime.createSession(new CreateSessionRequest(sessionFile, tempDir));

        assertThat(session).isInstanceOf(CodingAgentSession.class);
        assertThat(session.id()).isNotBlank();
        assertThat(session.sessionFile()).isEqualTo(sessionFile.toAbsolutePath().normalize());
        assertThat(session.cwd()).isEqualTo(tempDir.toAbsolutePath().normalize());
        assertThat(session.activeEntryId()).isNull();
        assertThat(session.conversationContext().transcriptMessages()).isEmpty();
        assertThat(session.conversationContext().generatedMessages()).isEmpty();
        assertThat(Files.readAllLines(sessionFile)).hasSize(1);

        SessionManager reopened = SessionManager.open(sessionFile);
        assertThat(reopened.document().header().header().orElseThrow().id()).isEqualTo(session.id());
        assertThat(reopened.activePath()).isEmpty();
    }

    @Test
    void createSessionAppendsOptionalNameAndModelEntries() throws Exception {
        Path sessionFile = tempDir.resolve("named.jsonl");
        CodingAgentSessionRuntime runtime = new CodingAgentSessionRuntime();

        AgentSession session = runtime.createSession(new CreateSessionRequest(
                sessionFile,
                tempDir,
                Optional.of("work session"),
                Optional.of(new AiModelReference("openai", "gpt-5"))));

        SessionManager reopened = SessionManager.open(sessionFile);
        assertThat(reopened.activePath()).extracting(SessionEntry::type)
                .containsExactly(
                        SessionEntryType.SESSION_INFO,
                        SessionEntryType.MODEL_CHANGE);
        assertThat(reopened.activePath().get(0).sessionInfo().orElseThrow().optionalName())
                .contains("work session");
        assertThat(reopened.activePath().get(1).modelChange().orElseThrow().provider())
                .isEqualTo("openai");
        assertThat(reopened.activePath().get(1).modelChange().orElseThrow().modelId())
                .isEqualTo("gpt-5");
        assertThat(session.activeEntryId()).isEqualTo(reopened.activeEntryId());
        assertThat(session.conversationContext().transcriptMessages()).isEmpty();
    }

    @Test
    void createSessionRejectsExistingFileThroughSessionManager() throws Exception {
        Path sessionFile = tempDir.resolve("existing.jsonl");
        Files.writeString(sessionFile, "");

        assertThatThrownBy(() -> new CodingAgentSessionRuntime()
                        .createSession(new CreateSessionRequest(sessionFile, tempDir)))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("already exists");
    }
}
