package com.agent4j.core.tool;

import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;

public interface ToolExecutionHook {
    default void beforeToolExecution(ToolCall toolCall, ToolContext context) throws Exception {
    }

    default void afterToolExecution(ToolCall toolCall, ToolContext context, ToolResult result) throws Exception {
    }
}
