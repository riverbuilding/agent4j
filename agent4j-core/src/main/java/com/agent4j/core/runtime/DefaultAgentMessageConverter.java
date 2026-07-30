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
import com.agent4j.core.message.AgentMessageView;
import com.agent4j.core.message.AssistantAgentMessageView;
import com.agent4j.core.message.ToolResultAgentMessageView;
import com.agent4j.core.message.UserAgentMessageView;

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
        return convertView(message.view());
    }

    private Optional<AiMessage> convertView(AgentMessageView view) {
        return switch (view) {
            case UserAgentMessageView user -> Optional.of(new AiUserMessage(aiContent(user.envelope())));
            case AssistantAgentMessageView assistant -> Optional.of(new AiAssistantMessage(
                    aiContent(assistant.envelope()),
                    AiStopReason.STOP,
                    AiUsage.zero()));
            case ToolResultAgentMessageView toolResult -> Optional.of(new AiToolResultMessage(
                    toolResult.toolCallId(),
                    toolResult.toolName(),
                    aiContent(toolResult.envelope()),
                    toolResult.error()));
            case com.agent4j.core.message.CustomAgentMessageView ignored -> Optional.empty();
            case com.agent4j.core.message.UnknownAgentMessageView ignored -> Optional.empty();
        };
    }

    private static List<AiContentBlock> aiContent(AgentMessage message) {
        return AiContentBlocks.parse(message.content());
    }
}
