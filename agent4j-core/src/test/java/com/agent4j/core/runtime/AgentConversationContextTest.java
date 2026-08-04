package com.agent4j.core.runtime;

import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiSystemMessage;
import com.agent4j.ai.AiTextContent;
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

class AgentConversationContextTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void ownsTranscriptAndGeneratedMessagesSeparately() {
        AgentMessage existing = message("user-1", AgentMessageRole.USER, "hello");
        AgentMessage generated = message("assistant-1", AgentMessageRole.ASSISTANT, "hi");
        AgentConversationContext context = new AgentConversationContext(List.of(existing), List.of());

        context.appendGenerated(generated);

        assertThat(context.transcriptMessages()).extracting(AgentMessage::id)
                .containsExactly("user-1", "assistant-1");
        assertThat(context.generatedMessages()).extracting(AgentMessage::id)
                .containsExactly("assistant-1");
        assertThat(context.assistantMessages()).extracting(AgentMessage::id)
                .containsExactly("assistant-1");
    }

    @Test
    void recordsGeneratedCompactionSummaryWithoutAppendingItTwiceToReplacedTranscript() {
        AgentMessage summary = message("summary-1", AgentMessageRole.COMPACTION_SUMMARY, "summary");
        AgentMessage retained = message("assistant-2", AgentMessageRole.ASSISTANT, "tail");
        AgentConversationContext context = new AgentConversationContext(
                List.of(message("user-1", AgentMessageRole.USER, "hello")),
                List.of());

        context.replaceTranscript(List.of(summary, retained));
        context.recordGenerated(summary);

        assertThat(context.transcriptMessages()).extracting(AgentMessage::id)
                .containsExactly("summary-1", "assistant-2");
        assertThat(context.generatedMessages()).extracting(AgentMessage::id)
                .containsExactly("summary-1");
        assertThat(context.assistantMessages()).isEmpty();
    }

    @Test
    void rebuildsModelMessagesFromCurrentTranscriptAtBoundary() {
        AgentConversationContext context = new AgentConversationContext(
                List.of(message("user-1", AgentMessageRole.USER, "hello")),
                List.of());

        List<AiMessage> modelMessages = context.toModelMessages("system", DefaultAgentMessageConverter.INSTANCE);

        assertThat(modelMessages).hasSize(2);
        assertThat(modelMessages.getFirst()).isEqualTo(new AiSystemMessage("system"));
        assertThat(modelMessages.get(1)).isEqualTo(AiUserMessage.text("hello"));
        assertThat(((AiUserMessage) modelMessages.get(1)).content()).containsExactly(new AiTextContent("hello"));
    }

    private static AgentMessage message(String id, AgentMessageRole role, String text) {
        return new AgentMessage(
                id,
                null,
                Instant.EPOCH,
                role,
                ContentBlocks.toJsonArray(List.of(new TextBlock(text, null))),
                JSON.objectNode());
    }
}
