package com.agent4j.coding.message;

import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiUserMessage;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageView;
import com.agent4j.core.message.CustomAgentMessageView;
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
        return convertView(message.view());
    }

    private Optional<AiMessage> convertView(AgentMessageView view) {
        return switch (view) {
            case CustomAgentMessageView custom -> switch (custom.role()) {
                case BASH_EXECUTION -> Optional.of(AiUserMessage.text(BashExecutionMessage.from(custom).toLlmText()));
                case BRANCH_SUMMARY -> Optional.of(AiUserMessage.text(BranchSummaryMessage.from(custom).toLlmText()));
                case COMPACTION_SUMMARY -> Optional.of(AiUserMessage.text(CompactionSummaryMessage.from(custom).toLlmText()));
                case CUSTOM -> Optional.of(AiUserMessage.text(CustomSessionMessage.from(custom).toLlmText()));
                case SYSTEM -> standardConverter.convertToLlm(List.of(custom.envelope())).stream().findFirst();
                case USER, ASSISTANT, TOOL_RESULT, UNKNOWN -> Optional.empty();
            };
            case com.agent4j.core.message.UserAgentMessageView user ->
                    standardConverter.convertToLlm(List.of(user.envelope())).stream().findFirst();
            case com.agent4j.core.message.AssistantAgentMessageView assistant ->
                    standardConverter.convertToLlm(List.of(assistant.envelope())).stream().findFirst();
            case com.agent4j.core.message.ToolResultAgentMessageView toolResult ->
                    standardConverter.convertToLlm(List.of(toolResult.envelope())).stream().findFirst();
            case com.agent4j.core.message.UnknownAgentMessageView unknown ->
                    standardConverter.convertToLlm(List.of(unknown.envelope())).stream().findFirst();
        };
    }
}
