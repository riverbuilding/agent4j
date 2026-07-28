package com.agent4j.core.tool;

import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;

@FunctionalInterface
public interface Tool {
    ToolResult execute(ToolCall call, ToolContext context) throws Exception;
}
