package com.agent4j.coding.extension;

import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.CloneSessionRequest;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CreateSessionRequest;
import com.agent4j.coding.sdk.ForkSessionRequest;
import com.agent4j.coding.sdk.ImportSessionRequest;
import com.agent4j.coding.sdk.ResumeSessionRequest;
import com.agent4j.coding.sdk.PromptRequest;
import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderRegistry;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.core.compaction.CompactionConfig;
import com.agent4j.testkit.ai.FakeModelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtensionSessionLifecycleIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void notifiesAroundCreateResumeCloneForkImportAndCloseInOperationOrder() throws Exception {
        List<String> notifications = new ArrayList<>();
        CodingAgentRuntime runtime = runtime(new ExtensionLifecycleListener() {
            @Override
            public void beforeSessionOperation(ExtensionSessionOperation operation, ExtensionSessionContext context) {
                notifications.add("before:" + operation);
                assertThat(context.session().sessionFile()).isAbsolute();
            }

            @Override
            public void afterSessionOperation(ExtensionSessionOperation operation, ExtensionSessionContext context) {
                notifications.add("after:" + operation + ":" + context.session().sessionId().isPresent());
            }
        });
        AgentSession source = runtime.createSession(new CreateSessionRequest(tempDir.resolve("source.jsonl"), tempDir));
        runtime.resumeSession(new ResumeSessionRequest(source.sessionFile()));
        runtime.cloneSession(new CloneSessionRequest(source, tempDir.resolve("clone.jsonl")));
        runtime.forkSession(new ForkSessionRequest(source, tempDir.resolve("fork.jsonl")));
        AgentSession imported = runtime.importSession(new ImportSessionRequest(source.sessionFile(), tempDir.resolve("import.jsonl")));
        imported.close();

        assertThat(notifications).containsExactly(
                "before:CREATE", "after:CREATE:true",
                "before:RESUME", "after:RESUME:true",
                "before:CLONE", "after:CLONE:true",
                "before:FORK", "after:FORK:true",
                "before:IMPORT", "after:IMPORT:true",
                "before:CLOSE", "after:CLOSE:true");
    }

    @Test
    void preOperationFailurePreventsSessionPersistence() {
        CodingAgentRuntime runtime = runtime(new ExtensionLifecycleListener() {
            @Override
            public void beforeSessionOperation(ExtensionSessionOperation operation, ExtensionSessionContext context) {
                throw new IllegalStateException("blocked by extension");
            }
        });
        Path sessionFile = tempDir.resolve("blocked.jsonl");

        assertThatThrownBy(() -> runtime.createSession(new CreateSessionRequest(sessionFile, tempDir)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("blocked by extension");
        assertThat(Files.exists(sessionFile)).isFalse();
    }

    @Test
    void postOperationFailureDoesNotUndoSuccessfulPersistence() throws Exception {
        CodingAgentRuntime runtime = runtime(new ExtensionLifecycleListener() {
            @Override
            public void afterSessionOperation(ExtensionSessionOperation operation, ExtensionSessionContext context) {
                throw new IllegalStateException("expected post-operation failure");
            }
        });
        Path sessionFile = tempDir.resolve("created.jsonl");

        AgentSession session = runtime.createSession(new CreateSessionRequest(sessionFile, tempDir));

        assertThat(session.sessionFile()).isEqualTo(sessionFile.toAbsolutePath().normalize());
        assertThat(Files.exists(sessionFile)).isTrue();
    }

    @Test
    void notifiesBeforeAndAfterACompactionThatPersists() throws Exception {
        List<String> notifications = new ArrayList<>();
        FakeModelClient model = new FakeModelClient()
                .enqueue(assistantText("assistant-1", "first answer"))
                .enqueue(assistantText("summary-1", "summary"));
        ExtensionLifecycleListener listener = new ExtensionLifecycleListener() {
            @Override
            public void beforeSessionOperation(ExtensionSessionOperation operation, ExtensionSessionContext context) {
                notifications.add("before:" + operation);
            }

            @Override
            public void afterSessionOperation(ExtensionSessionOperation operation, ExtensionSessionContext context) {
                notifications.add("after:" + operation);
            }
        };
        AgentExtension extension = new AgentExtension() {
            @Override public String name() { return "lifecycle"; }
            @Override public void register(ExtensionContext context, ExtensionContributionRegistrar registrar) {
                registrar.registerLifecycleListener("listener", listener);
            }
        };
        CodingAgentRuntime runtime = CodingAgentRuntime.builder()
                .providerRegistry(AiProviderRegistry.fixedClient(
                        new AiModel(new AiModelReference("test", "fixed"), "Fixed model"), model))
                .extensionLoader(ExtensionLoader.builder().addExtension(extension).build())
                .build();
        AgentSession session = runtime.createSession(new CreateSessionRequest(tempDir.resolve("compact.jsonl"), tempDir));
        notifications.clear();
        session.prompt(new PromptRequest("first prompt"));

        session.compact("preserve", CompactionConfig.builder().keepTokens(0).keepMessages(1).build());

        assertThat(notifications).containsExactly("before:COMPACT", "after:COMPACT");
    }

    private static CodingAgentRuntime runtime(ExtensionLifecycleListener listener) {
        AgentExtension extension = new AgentExtension() {
            @Override
            public String name() {
                return "lifecycle";
            }

            @Override
            public void register(ExtensionContext context, ExtensionContributionRegistrar registrar) {
                registrar.registerLifecycleListener("listener", listener);
            }
        };
        return CodingAgentRuntime.builder()
                .extensionLoader(ExtensionLoader.builder().addExtension(extension).build())
                .build();
    }

    private static List<AiStreamEvent> assistantText(String id, String text) {
        return List.of(new AiStreamEvent.MessageCompleted(
                id,
                new AiAssistantMessage(List.of(new AiTextContent(text)), AiStopReason.STOP, AiUsage.zero())));
    }
}
