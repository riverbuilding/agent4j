package com.agent4j.core.runtime;

import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.ToolResult;

import java.util.List;
import java.util.Objects;

public record AgentLoopResult(List<AgentMessage> assistantMessages, List<ToolResult> toolResults, Usage usage) {
    public AgentLoopResult {
        Objects.requireNonNull(assistantMessages, "assistantMessages");
        Objects.requireNonNull(toolResults, "toolResults");
        Objects.requireNonNull(usage, "usage");
        assistantMessages = List.copyOf(assistantMessages);
        toolResults = List.copyOf(toolResults);
    }
}
