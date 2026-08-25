package com.agent4j.examples;

import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.ToolRegistry;
import com.agent4j.core.tool.ToolSpec;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

final class WorkspaceStatusTool {
    private static final String NAME = "workspace_status";
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private WorkspaceStatusTool() {
    }

    static ToolRegistry registry() {
        return InMemoryToolRegistry.builder()
                .register(new ToolSpec(
                        NAME,
                        "Reports the agent workspace path. This tool never reads, writes, deletes, or executes anything.",
                        JSON.objectNode().put("type", "object").set("properties", JSON.objectNode())),
                        WorkspaceStatusTool::execute)
                .build();
    }

    private static ToolResult execute(ToolCall call, com.agent4j.core.tool.ToolContext context) {
        return new ToolResult(
                call.id(),
                NAME,
                false,
                JSON.objectNode()
                        .put("workspace", context.cwd().toString())
                        .put("sideEffects", "none"),
                JSON.objectNode());
    }
}
