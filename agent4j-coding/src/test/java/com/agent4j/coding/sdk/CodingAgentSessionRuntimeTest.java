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

    @Test
    void resumeSessionRestoresActiveConversationAndCanContinuePrompting() throws Exception {
        Path sessionFile = tempDir.resolve("resume.jsonl");
        new CodingAgentSessionRuntime(new FakeModelClient()
                        .enqueue(assistantText("assistant-1", "first answer", AiUsage.zero())))
                .createSession(new CreateSessionRequest(sessionFile, tempDir))
                .prompt(new PromptRequest("first prompt"));
        FakeModelClient resumedModel = new FakeModelClient()
                .enqueue(assistantText("assistant-2", "second answer", AiUsage.zero()));

        AgentSession resumed = new CodingAgentSessionRuntime(resumedModel)
                .resumeSession(new ResumeSessionRequest(sessionFile));

        assertThat(resumed.sessionFile()).isEqualTo(sessionFile.toAbsolutePath().normalize());
        assertThat(resumed.cwd()).isEqualTo(tempDir.toAbsolutePath().normalize());
        assertThat(resumed.conversationContext().transcriptMessages()).extracting(AgentMessage::textContent)
                .containsExactly("first prompt", "first answer");
        assertThat(resumed.conversationContext().generatedMessages()).isEmpty();

        resumed.prompt(new PromptRequest("second prompt"));

        assertThat(resumedModel.requests()).hasSize(1);
        assertThat(resumedModel.requests().getFirst().messages()).hasSize(3);
        assertThat(text(resumedModel.requests().getFirst().messages().get(0))).isEqualTo("first prompt");
        assertThat(text(resumedModel.requests().getFirst().messages().get(1))).isEqualTo("first answer");
        assertThat(text(resumedModel.requests().getFirst().messages().get(2))).isEqualTo("second prompt");
        assertThat(SessionManager.open(sessionFile).activeAgentMessages()).extracting(AgentMessage::textContent)
                .containsExactly("first prompt", "first answer", "second prompt", "second answer");
    }

    @Test
    void resumeSessionCanNavigateToSpecificActiveEntryBeforeContinuing() throws Exception {
        Path sessionFile = tempDir.resolve("resume-branch.jsonl");
        AgentSession session = new CodingAgentSessionRuntime(new FakeModelClient()
                        .enqueue(assistantText("assistant-1", "first answer", AiUsage.zero()))
                        .enqueue(assistantText("assistant-2", "second answer", AiUsage.zero())))
                .createSession(new CreateSessionRequest(sessionFile, tempDir));
        session.prompt(new PromptRequest("first prompt"));
        String firstAssistantId = SessionManager.open(sessionFile).activeAgentMessages().get(1).id();
        session.prompt(new PromptRequest("second prompt"));
        FakeModelClient branchedModel = new FakeModelClient()
                .enqueue(assistantText("assistant-3", "branched answer", AiUsage.zero()));

        AgentSession resumed = new CodingAgentSessionRuntime(branchedModel)
                .resumeSession(new ResumeSessionRequest(
                        sessionFile,
                        Optional.of(firstAssistantId),
                        Optional.empty()));

        assertThat(resumed.activeEntryId()).isEqualTo(firstAssistantId);
        assertThat(resumed.conversationContext().transcriptMessages()).extracting(AgentMessage::textContent)
                .containsExactly("first prompt", "first answer");

        resumed.prompt(new PromptRequest("branched prompt"));

        assertThat(branchedModel.requests()).hasSize(1);
        assertThat(branchedModel.requests().getFirst().messages()).hasSize(3);
        assertThat(text(branchedModel.requests().getFirst().messages().get(2))).isEqualTo("branched prompt");
        assertThat(SessionManager.open(sessionFile).activeAgentMessages()).extracting(AgentMessage::textContent)
                .containsExactly("first prompt", "first answer", "branched prompt", "branched answer");
    }

    @Test
    void importSessionCopiesValidatedJsonlAndReturnsSessionHandle() throws Exception {
        Path sourceFile = tempDir.resolve("import-source.jsonl");
        Files.writeString(sourceFile, """
                {"type":"session","version":3,"id":"session-1","timestamp":"2026-07-28T10:00:00Z","cwd":"/repo"}
                {"type":"message","id":"root0001","parentId":null,"timestamp":"2026-07-28T10:00:01Z","message":{"role":"user","content":"root"}}
                """);
        Path targetFile = tempDir.resolve("import-target.jsonl");

        AgentSession imported = new CodingAgentSessionRuntime()
                .importSession(new ImportSessionRequest(sourceFile, targetFile));

        assertThat(Files.readString(targetFile)).isEqualTo(Files.readString(sourceFile));
        assertThat(imported.id()).isEqualTo("session-1");
        assertThat(imported.sessionFile()).isEqualTo(targetFile.toAbsolutePath().normalize());
        assertThat(imported.cwd()).isEqualTo(Path.of("/repo").toAbsolutePath().normalize());
        assertThat(imported.activeEntryId()).isEqualTo("root0001");
        assertThat(imported.conversationContext().transcriptMessages()).extracting(AgentMessage::textContent)
                .containsExactly("root");
    }

    @Test
    void cloneSessionCopiesFullDocumentAndReturnsClonedSessionHandle() throws Exception {
        Path sourceFile = tempDir.resolve("clone-source.jsonl");
        AgentSession source = new CodingAgentSessionRuntime(new FakeModelClient()
                        .enqueue(assistantText("assistant-1", "first answer", AiUsage.zero()))
                        .enqueue(assistantText("assistant-2", "second answer", AiUsage.zero())))
                .createSession(new CreateSessionRequest(sourceFile, tempDir));
        source.prompt(new PromptRequest("first prompt"));
        source.prompt(new PromptRequest("second prompt"));
        Path targetFile = tempDir.resolve("clone-target.jsonl");

        AgentSession clone = new CodingAgentSessionRuntime()
                .cloneSession(new CloneSessionRequest(source, targetFile));

        assertThat(Files.readString(targetFile)).isEqualTo(Files.readString(sourceFile));
        assertThat(clone.id()).isEqualTo(source.id());
        assertThat(clone.sessionFile()).isEqualTo(targetFile.toAbsolutePath().normalize());
        assertThat(clone.activeEntryId()).isEqualTo(source.activeEntryId());
        assertThat(clone.conversationContext().transcriptMessages()).extracting(AgentMessage::textContent)
                .containsExactly("first prompt", "first answer", "second prompt", "second answer");
    }

    @Test
    void forkSessionWritesOnlySelectedActivePathWithDerivedHeader() throws Exception {
        Path sourceFile = tempDir.resolve("fork-source.jsonl");
        AgentSession source = new CodingAgentSessionRuntime(new FakeModelClient()
                        .enqueue(assistantText("assistant-1", "first answer", AiUsage.zero()))
                        .enqueue(assistantText("assistant-2", "second answer", AiUsage.zero())))
                .createSession(new CreateSessionRequest(sourceFile, tempDir));
        source.prompt(new PromptRequest("first prompt"));
        String firstAssistantId = SessionManager.open(sourceFile).activeAgentMessages().get(1).id();
        source.prompt(new PromptRequest("second prompt"));
        Path forkFile = tempDir.resolve("fork-target.jsonl");

        AgentSession fork = new CodingAgentSessionRuntime()
                .forkSession(new ForkSessionRequest(source, forkFile, Optional.of(firstAssistantId)));

        assertThat(fork.sessionFile()).isEqualTo(forkFile.toAbsolutePath().normalize());
        assertThat(fork.activeEntryId()).isEqualTo(firstAssistantId);
        assertThat(fork.conversationContext().transcriptMessages()).extracting(AgentMessage::textContent)
                .containsExactly("first prompt", "first answer");
        SessionManager forkedManager = SessionManager.open(forkFile);
        assertThat(forkedManager.document().header().header().orElseThrow().sourceSessionId()).contains(source.id());
        assertThat(forkedManager.document().header().header().orElseThrow().forkedFromEntryId())
                .contains(firstAssistantId);
        assertThat(Files.readString(forkFile)).doesNotContain("second prompt");
        assertThat(Files.readString(forkFile)).doesNotContain("second answer");
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
