package com.agent4j.core.tool;

import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.runtime.AgentAbortException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Objects;

public final class ToolExecutor {
    private final ToolRegistry registry;

    public ToolExecutor(ToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public ToolResult execute(ToolCall call, ToolContext context) {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(context, "context");
        context.abortSignal().throwIfAborted();
        RegisteredTool registeredTool = registry.find(call.name()).orElse(null);
        if (registeredTool == null) {
            return error(call, "unknown tool: " + call.name(), null);
        }
        try {
            ToolResult result = registeredTool.tool().execute(call, context);
            return result == null ? error(call, "tool returned null result", null) : result;
        } catch (AgentAbortException e) {
            throw e;
        } catch (Exception e) {
            return error(call, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e.getClass().getName());
        }
    }

    private static ToolResult error(ToolCall call, String message, String exceptionClass) {
        ObjectNode metadata = JsonNodeFactory.instance.objectNode();
        metadata.put("message", message);
        if (exceptionClass != null) {
            metadata.put("exceptionClass", exceptionClass);
        }
        return new ToolResult(call.id(), call.name(), true, TextNode.valueOf(message), metadata);
    }
}
