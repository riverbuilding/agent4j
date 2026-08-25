package com.agent4j.coding.message;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelClientProvider;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.ai.AiUserMessage;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.agent4j.core.runtime.AbortController;
import com.agent4j.core.runtime.AgentLoopOptions;
import com.agent4j.core.runtime.AgentLoop;
import com.agent4j.core.runtime.AgentLoopRequest;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.testkit.ai.FakeModelClient;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodingAgentMessageConverterTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void convertsCodingSessionMessagesIntoLlmUserContext() {
        ObjectNode bashMetadata = JSON.objectNode()
                .put("command", "pwd")
                .put("exitCode", 0);
        ObjectNode customMetadata = JSON.objectNode().put("customType", "extension.note");

        List<AiMessage> messages = CodingAgentMessageConverter.INSTANCE.convertToLlm(List.of(
                message("bash-1", AgentMessageRole.BASH_EXECUTION, "repo\n", bashMetadata),
                message("branch-1", AgentMessageRole.BRANCH_SUMMARY, "branch kept README context", JSON.objectNode()),
                message("compact-1", AgentMessageRole.COMPACTION_SUMMARY, "summarized old turns", JSON.objectNode()),
                message("custom-1", AgentMessageRole.CUSTOM, "custom content", customMetadata)));

        assertThat(messages).hasSize(4);
        assertThat(messages).allMatch(message -> message instanceof AiUserMessage);
        assertThat(text(messages.get(0))).isEqualTo("""
                <bashExecution>
                Command: pwd
                Exit code: 0
                Output:
                repo

                </bashExecution>""");
        assertThat(text(messages.get(1))).isEqualTo("""
                <branchSummary>
                branch kept README context
                </branchSummary>""");
        assertThat(text(messages.get(2))).isEqualTo("""
                <compactionSummary>
                summarized old turns
                </compactionSummary>""");
        assertThat(text(messages.get(3))).isEqualTo("""
                <customMessage type="extension.note">
                custom content
                </customMessage>""");
    }

    @Test
    void escapesCustomMessageTypeWhenRenderingPromptWrapper() {
        ObjectNode metadata = JSON.objectNode().put("customType", "vendor\" <note>&");

        List<AiMessage> messages = CodingAgentMessageConverter.INSTANCE.convertToLlm(List.of(
                message("custom-1", AgentMessageRole.CUSTOM, "custom content", metadata)));

        assertThat(messages).hasSize(1);
        assertThat(text(messages.getFirst())).isEqualTo("""
                <customMessage type="vendor&quot; &lt;note&gt;&amp;">
                custom content
                </customMessage>""");
    }

    @Test
    void usesStableDefaultCustomTypeWhenMissing() {
        List<AiMessage> messages = CodingAgentMessageConverter.INSTANCE.convertToLlm(List.of(
                message("custom-1", AgentMessageRole.CUSTOM, "custom content", JSON.objectNode())));

        assertThat(text(messages.getFirst())).startsWith("<customMessage type=\"custom\">");
    }

    @Test
    void skipsUnknownCustomRolesAtCodingBoundary() {
        List<AiMessage> messages = CodingAgentMessageConverter.INSTANCE.convertToLlm(List.of(
                message("unknown-1", AgentMessageRole.UNKNOWN, "unknown content", JSON.objectNode())));

        assertThat(messages).isEmpty();
    }

    @Test
    void preservesStandardRoleConversionWhileAddingCodingMessages() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueue(List.of(
                new AiStreamEvent.MessageStarted("assistant-1"),
                new AiStreamEvent.MessageCompleted(
                        "assistant-1",
                        new AiAssistantMessage(
                                List.of(new AiTextContent("done")),
                                AiStopReason.STOP,
                                AiUsage.zero()))));
        AgentMessage bash = message("bash-1", AgentMessageRole.BASH_EXECUTION, "file list", JSON.objectNode()
                .put("command", "ls")
                .put("exitCode", 0));
        AgentMessage user = message("user-1", AgentMessageRole.USER, "summarize", JSON.objectNode());

        AiModel fixedModel = new AiModel(new AiModelReference("test", "fixed"), "Fixed model");
        new AgentLoop(
                new AiModelClientProvider(fixedModel, model),
                fixedModel,
                InMemoryToolRegistry.builder().build(),
                new AgentEventBus(),
                CodingAgentMessageConverter.INSTANCE)
                .runTurn(new AgentLoopRequest(
                        "session-1",
                        "turn-1",
                        user.id(),
                        List.of(bash, user),
                        Path.of("/repo"),
                        clock,
                        new AbortController().signal(),
                        AgentLoopOptions.builder()
                                .maxToolRounds(1)
                                .promptMessages(List.of(user))
                                .build()));

        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().getFirst().messages()).extracting(AiMessage::role)
                .containsExactly("user", "user");
        assertThat(text(model.requests().getFirst().messages().getFirst())).contains("<bashExecution>");
        assertThat(text(model.requests().getFirst().messages().get(1))).isEqualTo("summarize");
    }

    private AgentMessage message(String id, AgentMessageRole role, String text, com.fasterxml.jackson.databind.JsonNode metadata) {
        return new AgentMessage(
                id,
                null,
                Instant.now(clock),
                role,
                ContentBlocks.toJsonArray(List.of(new TextBlock(text, null))),
                metadata);
    }

    private static String text(AiMessage message) {
        return ((AiTextContent) ((AiUserMessage) message).content().getFirst()).text();
    }
}
