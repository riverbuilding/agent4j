package com.agent4j.core.runtime;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiContentBlock;
import com.agent4j.ai.AiContentBlocks;
import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiToolResultMessage;
import com.agent4j.ai.AiUsage;
import com.agent4j.ai.AiUserMessage;
import com.agent4j.core.message.AgentMessage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DefaultAgentMessageConverter implements AgentMessageConverter {
    public static final DefaultAgentMessageConverter INSTANCE = new DefaultAgentMessageConverter();

    private DefaultAgentMessageConverter() {
    }

    @Override
    public List<AiMessage> convertToLlm(List<AgentMessage> messages) {
        Objects.requireNonNull(messages, "messages");
        return messages.stream()
                .map(this::convertMessage)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<AiMessage> convertMessage(AgentMessage message) {
        List<AiContentBlock> content = AiContentBlocks.parse(message.content());
        return switch (message.role()) {
            case USER -> Optional.of(new AiUserMessage(content));
            case ASSISTANT -> Optional.of(new AiAssistantMessage(content, AiStopReason.STOP, AiUsage.zero()));
            case TOOL_RESULT -> Optional.of(new AiToolResultMessage(
                    textMetadata(message, "toolCallId"),
                    textMetadata(message, "toolName"),
                    content,
                    booleanMetadata(message, "error")));
            case BASH_EXECUTION, CUSTOM, BRANCH_SUMMARY, COMPACTION_SUMMARY, SYSTEM, UNKNOWN -> Optional.empty();
        };
    }

    private static String textMetadata(AgentMessage message, String fieldName) {
        return message.metadata() != null && message.metadata().has(fieldName)
                ? message.metadata().get(fieldName).asText("")
                : "";
    }

    private static boolean booleanMetadata(AgentMessage message, String fieldName) {
        return message.metadata() != null && message.metadata().has(fieldName)
                && message.metadata().get(fieldName).asBoolean(false);
    }
}
