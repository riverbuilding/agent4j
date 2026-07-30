package com.agent4j.coding.message;

import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiUserMessage;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.runtime.AgentMessageConverter;
import com.agent4j.core.runtime.DefaultAgentMessageConverter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CodingAgentMessageConverter implements AgentMessageConverter {
    public static final CodingAgentMessageConverter INSTANCE = new CodingAgentMessageConverter();

    private final AgentMessageConverter standardConverter;

    public CodingAgentMessageConverter() {
        this(DefaultAgentMessageConverter.INSTANCE);
    }

    public CodingAgentMessageConverter(AgentMessageConverter standardConverter) {
        this.standardConverter = Objects.requireNonNull(standardConverter, "standardConverter");
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
        Objects.requireNonNull(message, "message");
        return switch (message.role()) {
            case BASH_EXECUTION -> Optional.of(AiUserMessage.text(BashExecutionMessage.from(message).toLlmText()));
            case BRANCH_SUMMARY -> Optional.of(AiUserMessage.text(BranchSummaryMessage.from(message).toLlmText()));
            case COMPACTION_SUMMARY -> Optional.of(AiUserMessage.text(CompactionSummaryMessage.from(message).toLlmText()));
            case CUSTOM -> Optional.of(AiUserMessage.text(CustomSessionMessage.from(message).toLlmText()));
            case USER, ASSISTANT, TOOL_RESULT, SYSTEM, UNKNOWN -> standardConverter.convertToLlm(List.of(message)).stream()
                    .findFirst();
        };
    }
}
