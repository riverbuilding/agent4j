package com.agent4j.core.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMessageViewTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant timestamp = Instant.parse("2026-07-30T10:00:00Z");

    @Test
    void mapsStandardRolesToTypedViews() {
        assertThat(message("user-1", AgentMessageRole.USER, "hello", JSON.objectNode()).view())
                .isInstanceOf(UserAgentMessageView.class);
        assertThat(message("assistant-1", AgentMessageRole.ASSISTANT, "hello", JSON.objectNode()).view())
                .isInstanceOf(AssistantAgentMessageView.class);
        assertThat(message("tool-result-1", AgentMessageRole.TOOL_RESULT, "hello", JSON.objectNode()).view())
                .isInstanceOf(ToolResultAgentMessageView.class);
    }

    @Test
    void mapsSessionOnlyRolesToCustomViewAndUnknownRoleToUnknownView() {
        assertThat(message("bash-1", AgentMessageRole.BASH_EXECUTION, "pwd", JSON.objectNode()).view())
                .isInstanceOf(CustomAgentMessageView.class);
        assertThat(message("branch-1", AgentMessageRole.BRANCH_SUMMARY, "branch", JSON.objectNode()).view())
                .isInstanceOf(CustomAgentMessageView.class);
        assertThat(message("compact-1", AgentMessageRole.COMPACTION_SUMMARY, "summary", JSON.objectNode()).view())
                .isInstanceOf(CustomAgentMessageView.class);
        assertThat(message("custom-1", AgentMessageRole.CUSTOM, "custom", JSON.objectNode()).view())
                .isInstanceOf(CustomAgentMessageView.class);
        assertThat(message("unknown-1", AgentMessageRole.UNKNOWN, "unknown", JSON.objectNode()).view())
                .isInstanceOf(UnknownAgentMessageView.class);
    }

    @Test
    void assistantViewExtractsToolCallsFromEnvelopeContent() throws Exception {
        AgentMessage message = new AgentMessage(
                "assistant-1",
                "user-1",
                timestamp,
                AgentMessageRole.ASSISTANT,
                mapper.readTree("""
                        [
                          {"type":"text","text":"reading"},
                          {"type":"toolCall","id":"tool-1","name":"read","arguments":{"path":"README.md"}}
                        ]
                        """),
                JSON.objectNode());

        AssistantAgentMessageView view = (AssistantAgentMessageView) message.view();

        assertThat(view.envelope()).isSameAs(message);
        assertThat(view.text()).isEqualTo("reading");
        assertThat(view.toolCalls()).hasSize(1);
        assertThat(view.toolCalls().getFirst().id()).isEqualTo("tool-1");
        assertThat(view.toolCalls().getFirst().name()).isEqualTo("read");
        assertThat(view.toolCalls().getFirst().arguments().path("path").asText()).isEqualTo("README.md");
    }

    @Test
    void toolResultViewExposesStableMetadataAndPreservesEnvelope() {
        AgentMessage message = message(
                "tool-result-1",
                AgentMessageRole.TOOL_RESULT,
                "blocked",
                JSON.objectNode()
                        .put("toolCallId", "tool-1")
                        .put("toolName", "write")
                        .put("error", true)
                        .put("blocked", true)
                        .put("terminate", true)
                        .put("futureField", "kept"));

        ToolResultAgentMessageView view = (ToolResultAgentMessageView) message.view();

        assertThat(view.envelope()).isSameAs(message);
        assertThat(view.toolCallId()).isEqualTo("tool-1");
        assertThat(view.toolName()).isEqualTo("write");
        assertThat(view.error()).isTrue();
        assertThat(view.blocked()).isTrue();
        assertThat(view.terminate()).isTrue();
        assertThat(view.envelope().metadata().path("futureField").asText()).isEqualTo("kept");
    }

    @Test
    void toolResultViewCreatesEnvelopeAndPreservesResultMetadata() {
        ToolResult result = new ToolResult(
                "tool-1",
                "write",
                true,
                JSON.textNode("blocked"),
                JSON.objectNode()
                        .put("blocked", true)
                        .put("futureField", "kept"));

        AgentMessage message = ToolResultAgentMessageView.toEnvelope(result, "assistant-1", timestamp);

        assertThat(message.id()).isEqualTo("tool-result-tool-1");
        assertThat(message.parentId()).isEqualTo("assistant-1");
        assertThat(message.timestamp()).isEqualTo(timestamp);
        assertThat(message.role()).isEqualTo(AgentMessageRole.TOOL_RESULT);
        assertThat(message.content().asText()).isEqualTo("blocked");
        assertThat(message.metadata().path("toolCallId").asText()).isEqualTo("tool-1");
        assertThat(message.metadata().path("toolName").asText()).isEqualTo("write");
        assertThat(message.metadata().path("error").asBoolean()).isTrue();
        assertThat(message.metadata().path("blocked").asBoolean()).isTrue();
        assertThat(message.metadata().path("futureField").asText()).isEqualTo("kept");
    }

    @Test
    void customViewExposesCustomMetadataAndRawEnvelope() {
        AgentMessage message = message(
                "custom-1",
                AgentMessageRole.CUSTOM,
                "custom body",
                JSON.objectNode()
                        .put("customType", "extension.note")
                        .put("futureField", "kept"));

        CustomAgentMessageView view = (CustomAgentMessageView) message.view();

        assertThat(view.envelope()).isSameAs(message);
        assertThat(view.text()).isEqualTo("custom body");
        assertThat(view.customType()).contains("extension.note");
        assertThat(view.field("futureField")).hasValueSatisfying(value ->
                assertThat(value.asText()).isEqualTo("kept"));
    }

    private AgentMessage message(
            String id,
            AgentMessageRole role,
            String text,
            com.fasterxml.jackson.databind.JsonNode metadata
    ) {
        return new AgentMessage(
                id,
                null,
                timestamp,
                role,
                ContentBlocks.toJsonArray(List.of(new TextBlock(text, null))),
                metadata);
    }
}
