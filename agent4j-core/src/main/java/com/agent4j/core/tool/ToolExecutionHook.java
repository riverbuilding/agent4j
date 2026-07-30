package com.agent4j.core.tool;

import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;

import java.util.Optional;

public interface ToolExecutionHook {
    default Optional<ToolResult> beforeToolExecution(ToolCall toolCall, ToolContext context) throws Exception {
        return Optional.empty();
    }

    default void afterToolExecution(ToolCall toolCall, ToolContext context, ToolResult result) throws Exception {
    }
}
