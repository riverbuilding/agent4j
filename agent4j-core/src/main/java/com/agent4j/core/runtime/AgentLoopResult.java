package com.agent4j.core.runtime;

import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.ToolResult;

import java.util.List;
import java.util.Objects;

public record AgentLoopResult(
        List<AgentMessage> messages,
        List<AgentMessage> assistantMessages,
        List<ToolResult> toolResults,
        Usage usage
) {
    public AgentLoopResult(List<AgentMessage> assistantMessages, List<ToolResult> toolResults, Usage usage) {
        this(assistantMessages, assistantMessages, toolResults, usage);
    }

    public AgentLoopResult {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(assistantMessages, "assistantMessages");
        Objects.requireNonNull(toolResults, "toolResults");
        Objects.requireNonNull(usage, "usage");
        messages = List.copyOf(messages);
        assistantMessages = List.copyOf(assistantMessages);
        toolResults = List.copyOf(toolResults);
    }
}
