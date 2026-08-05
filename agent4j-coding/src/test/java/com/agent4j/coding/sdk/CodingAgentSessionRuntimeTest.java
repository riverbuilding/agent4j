package com.agent4j.coding.sdk;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.ai.AiUserMessage;
import com.agent4j.coding.session.SessionEntry;
import com.agent4j.coding.session.SessionEntryType;
import com.agent4j.coding.session.SessionManager;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.runtime.Usage;
import com.agent4j.testkit.ai.FakeModelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    @Test
    void promptRunsModelPersistsResultAndRefreshesConversationContext() throws Exception {
        Path sessionFile = tempDir.resolve("prompt.jsonl");
        FakeModelClient model = new FakeModelClient().enqueue(assistantText("assistant-1", "hello", new AiUsage(3, 2, 1, 0)));
        CodingAgentSessionRuntime runtime = new CodingAgentSessionRuntime(model);
        AgentSession session = runtime.createSession(new CreateSessionRequest(sessionFile, tempDir));

        PromptResult result = session.prompt(new PromptRequest("say hello"));

        assertThat(result.session()).isSameAs(session);
        assertThat(result.loopResult().usage()).isEqualTo(new Usage(3, 2, 1, 0));
        assertThat(result.loopResult().messages()).extracting(AgentMessage::role)
                .containsExactly(AgentMessageRole.USER, AgentMessageRole.ASSISTANT);
        assertThat(result.persistedEntries()).extracting(SessionEntry::type)
                .containsExactly(SessionEntryType.MESSAGE, SessionEntryType.MESSAGE);
        assertThat(result.persistedEntries()).extracting(SessionEntry::parentId)
                .containsExactly(null, result.persistedEntries().getFirst().id());
        assertThat(session.conversationContext().transcriptMessages()).extracting(AgentMessage::role)
                .containsExactly(AgentMessageRole.USER, AgentMessageRole.ASSISTANT);
        assertThat(session.conversationContext().generatedMessages()).extracting(AgentMessage::role)
                .containsExactly(AgentMessageRole.USER, AgentMessageRole.ASSISTANT);

        SessionManager reopened = SessionManager.open(sessionFile);
        assertThat(reopened.activeAgentMessages()).extracting(AgentMessage::textContent)
                .containsExactly("say hello", "hello");
        assertThat(model.requests()).hasSize(1);
        assertThat(((AiTextContent) ((AiUserMessage) model.requests().getFirst().messages().getFirst())
                .content().getFirst()).text()).isEqualTo("say hello");
    }

    @Test
    void repeatedPromptUsesSessionOwnedHistoryWithoutCallerRebuildingMessages() throws Exception {
        Path sessionFile = tempDir.resolve("repeat.jsonl");
        FakeModelClient model = new FakeModelClient()
                .enqueue(assistantText("assistant-1", "first answer", AiUsage.zero()))
                .enqueue(assistantText("assistant-2", "second answer", AiUsage.zero()));
        CodingAgentSessionRuntime runtime = new CodingAgentSessionRuntime(model);
        AgentSession session = runtime.createSession(new CreateSessionRequest(sessionFile, tempDir));

        session.prompt(new PromptRequest("first prompt"));
        session.prompt(new PromptRequest("second prompt"));

        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages()).hasSize(3);
        assertThat(text(model.requests().get(1).messages().get(0))).isEqualTo("first prompt");
        assertThat(text(model.requests().get(1).messages().get(1))).isEqualTo("first answer");
        assertThat(text(model.requests().get(1).messages().get(2))).isEqualTo("second prompt");

        SessionManager reopened = SessionManager.open(sessionFile);
        assertThat(reopened.activeAgentMessages()).extracting(AgentMessage::textContent)
                .containsExactly("first prompt", "first answer", "second prompt", "second answer");
        assertThat(session.conversationContext().transcriptMessages()).extracting(AgentMessage::textContent)
                .containsExactly("first prompt", "first answer", "second prompt", "second answer");
    }

    @Test
    void promptRequiresConfiguredModelClient() throws Exception {
        AgentSession session = new CodingAgentSessionRuntime()
                .createSession(new CreateSessionRequest(tempDir.resolve("missing-model.jsonl"), tempDir));

        assertThatThrownBy(() -> session.prompt(new PromptRequest("hello")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model client");
    }

    private static List<AiStreamEvent> assistantText(String messageId, String text, AiUsage usage) {
        return List.of(new AiStreamEvent.MessageCompleted(
                messageId,
                new AiAssistantMessage(
                        List.of(new AiTextContent(text)),
                        AiStopReason.STOP,
                        usage)));
    }

    private static String text(com.agent4j.ai.AiMessage message) {
        return switch (message) {
            case AiUserMessage user -> ((AiTextContent) user.content().getFirst()).text();
            case AiAssistantMessage assistant -> ((AiTextContent) assistant.content().getFirst()).text();
            default -> throw new AssertionError("unexpected message type: " + message);
        };
    }
}
