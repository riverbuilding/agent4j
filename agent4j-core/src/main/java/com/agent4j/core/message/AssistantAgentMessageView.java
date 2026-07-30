package com.agent4j.core.message;

import java.util.List;
import java.util.Objects;

public record AssistantAgentMessageView(AgentMessage envelope) implements AgentMessageView {
    public AssistantAgentMessageView {
        Objects.requireNonNull(envelope, "envelope");
    }

    public List<ContentBlock> contentBlocks() {
        return envelope.contentBlocks();
    }

    public List<ToolCall> toolCalls() {
        return contentBlocks().stream()
                .filter(ToolCallBlock.class::isInstance)
                .map(ToolCallBlock.class::cast)
                .map(ToolCallBlock::toolCall)
                .toList();
    }

    public String text() {
        return envelope.textContent();
    }
}
