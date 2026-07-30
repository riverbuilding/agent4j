package com.agent4j.core.runtime;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiToolResultMessage;
import com.agent4j.ai.AiUserMessage;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentMessageConverterTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final Instant timestamp = Instant.parse("2026-07-30T10:00:00Z");

    @Test
    void convertsOnlyStandardTranscriptViewsToLlmMessages() {
        AgentMessage user = message("user-1", AgentMessageRole.USER, "hello", JSON.objectNode());
        AgentMessage assistant = message("assistant-1", AgentMessageRole.ASSISTANT, "hi", JSON.objectNode());
        AgentMessage toolResult = message(
                "tool-result-1",
                AgentMessageRole.TOOL_RESULT,
                "content",
                JSON.objectNode()
                        .put("toolCallId", "tool-1")
                        .put("toolName", "read")
                        .put("error", true)
                        .put("futureField", "kept"));
        AgentMessage custom = message("custom-1", AgentMessageRole.CUSTOM, "custom", JSON.objectNode());
        AgentMessage unknown = message("unknown-1", AgentMessageRole.UNKNOWN, "unknown", JSON.objectNode());

        List<AiMessage> converted = DefaultAgentMessageConverter.INSTANCE.convertToLlm(List.of(
                user,
                assistant,
                toolResult,
                custom,
                unknown));

        assertThat(converted).hasSize(3);
        assertThat(converted.get(0)).isInstanceOf(AiUserMessage.class);
        assertThat(text((AiUserMessage) converted.get(0))).isEqualTo("hello");
        assertThat(converted.get(1)).isInstanceOf(AiAssistantMessage.class);
        assertThat(text((AiAssistantMessage) converted.get(1))).isEqualTo("hi");
        assertThat(converted.get(2)).isInstanceOf(AiToolResultMessage.class);
        AiToolResultMessage aiToolResult = (AiToolResultMessage) converted.get(2);
        assertThat(aiToolResult.toolCallId()).isEqualTo("tool-1");
        assertThat(aiToolResult.toolName()).isEqualTo("read");
        assertThat(aiToolResult.error()).isTrue();
        assertThat(((AiTextContent) aiToolResult.content().getFirst()).text()).isEqualTo("content");
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

    private static String text(AiUserMessage message) {
        return ((AiTextContent) message.content().getFirst()).text();
    }

    private static String text(AiAssistantMessage message) {
        return ((AiTextContent) message.content().getFirst()).text();
    }
}
