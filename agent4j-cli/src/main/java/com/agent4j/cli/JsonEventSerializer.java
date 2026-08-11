package com.agent4j.cli;

import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Maps internal runtime events to the public PI JSON event-stream contract. */
public final class JsonEventSerializer {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ObjectMapper mapper;
    private final Map<String, ObjectNode> streamingMessages = new LinkedHashMap<>();
    private final Map<String, ToolCall> activeToolCalls = new LinkedHashMap<>();

    public JsonEventSerializer() {
        this(new ObjectMapper());
    }

    JsonEventSerializer(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public String serialize(JsonNode value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new UncheckedIOException(error);
        }
    }

    public ObjectNode event(AgentEvent event) {
        Objects.requireNonNull(event, "event");
        return switch (event) {
            case AgentEvent.AgentStarted ignored -> type("agent_start");
            case AgentEvent.AgentEnded ended -> type("agent_end")
                    .set("messages", messages(ended.messages()));
            case AgentEvent.TurnStarted ignored -> type("turn_start");
            case AgentEvent.TurnEnded ended -> turnEnd(ended);
            case AgentEvent.MessageStarted started -> messageStart(started.message());
            case AgentEvent.MessageUpdated updated -> messageUpdate(updated);
            case AgentEvent.MessageEnded ended -> messageEnd(ended.message());
            case AgentEvent.ToolExecutionStarted started -> toolExecutionStart(started.toolCall());
            case AgentEvent.ToolExecutionUpdated updated -> toolExecutionUpdate(updated);
            case AgentEvent.ToolExecutionEnded ended -> toolExecutionEnd(ended.result());
            case AgentEvent.QueueUpdated updated -> type("queue_update")
                    .put("queue", updated.queueKind().wireName())
                    .put("size", updated.size());
            case AgentEvent.RetryStarted started -> type("auto_retry_start")
                    .put("attempt", started.attempt())
                    .put("errorMessage", started.reason());
            case AgentEvent.RetryCompleted completed -> type("auto_retry_end")
                    .put("success", completed.success())
                    .put("attempt", completed.attempt());
            case AgentEvent.CompactionStarted started -> type("compaction_start")
                    .put("reason", started.reason());
            case AgentEvent.CompactionCompleted completed -> type("compaction_end")
                    .put("summaryMessageId", completed.summaryMessageId());
            case AgentEvent.AgentAborted aborted -> type("agent_aborted")
                    .put("reason", aborted.reason());
        };
    }

    public ObjectNode message(AgentMessage message) {
        Objects.requireNonNull(message, "message");
        ObjectNode value = JSON.objectNode().put("role", message.role().wireName());
        if (message.content() != null && !message.content().isNull()) {
            value.set("content", message.content().deepCopy());
        }
        if (message.metadata() != null && message.metadata().isObject()) {
            message.metadata().fields().forEachRemaining(field -> {
                if (!value.has(field.getKey())) {
                    value.set(field.getKey(), field.getValue().deepCopy());
                }
            });
        }
        return value;
    }

    private ObjectNode toolExecutionStart(ToolCall toolCall) {
        activeToolCalls.put(toolCall.id(), toolCall);
        return type("tool_execution_start")
                .put("toolCallId", toolCall.id())
                .put("toolName", toolCall.name())
                .set("args", toolCall.arguments().deepCopy());
    }

    private ObjectNode toolExecutionEnd(ToolResult result) {
        activeToolCalls.remove(result.toolCallId());
        ObjectNode event = type("tool_execution_end")
                .put("toolCallId", result.toolCallId())
                .put("toolName", result.toolName());
        event.set("result", result.content() == null ? JSON.nullNode() : result.content().deepCopy());
        event.put("isError", result.error());
        return event;
    }

    private ObjectNode turnEnd(AgentEvent.TurnEnded ended) {
        ObjectNode event = type("turn_end");
        event.set("message", message(ended.message()));
        event.set("toolResults", toolResults(ended.toolResults()));
        return event;
    }

    private ObjectNode messageStart(AgentMessage message) {
        ObjectNode snapshot = message(message);
        streamingMessages.put(message.id(), snapshot);
        ObjectNode event = type("message_start");
        event.set("message", snapshot.deepCopy());
        return event;
    }

    private ObjectNode messageUpdate(AgentEvent.MessageUpdated updated) {
        ObjectNode snapshot = streamingMessages.computeIfAbsent(updated.messageId(), ignored -> assistantMessage());
        applyDelta(snapshot, updated.delta());
        ObjectNode event = type("message_update");
        event.set("message", snapshot.deepCopy());
        event.set("assistantMessageEvent", updated.delta().deepCopy());
        return event;
    }

    private ObjectNode messageEnd(AgentMessage message) {
        streamingMessages.remove(message.id());
        ObjectNode event = type("message_end");
        event.set("message", message(message));
        return event;
    }

    private ObjectNode toolExecutionUpdate(AgentEvent.ToolExecutionUpdated updated) {
        ToolCall call = activeToolCalls.get(updated.toolCallId());
        ObjectNode event = type("tool_execution_update").put("toolCallId", updated.toolCallId());
        if (call != null) {
            event.put("toolName", call.name());
            event.set("args", call.arguments().deepCopy());
        }
        event.set("partialResult", updated.delta().deepCopy());
        return event;
    }

    private static ObjectNode assistantMessage() {
        return JSON.objectNode().put("role", "assistant").set("content", JSON.arrayNode());
    }

    private static void applyDelta(ObjectNode message, JsonNode delta) {
        if (delta == null || !delta.isObject()) {
            return;
        }
        String type = delta.path("type").asText();
        int contentIndex = delta.path("contentIndex").asInt(-1);
        if (contentIndex < 0) {
            return;
        }
        ArrayNode content = message.withArray("content");
        while (content.size() <= contentIndex) {
            content.addObject();
        }
        ObjectNode block = (ObjectNode) content.get(contentIndex);
        switch (type) {
            case "text_start" -> block.put("type", "text").put("text", "");
            case "text_delta" -> block.put("type", "text")
                    .put("text", block.path("text").asText() + delta.path("delta").asText());
            case "thinking_start" -> block.put("type", "thinking").put("thinking", "");
            case "thinking_delta" -> block.put("type", "thinking")
                    .put("thinking", block.path("thinking").asText() + delta.path("delta").asText());
            case "toolcall_start" -> {
                block.put("type", "toolCall");
                block.put("id", delta.path("toolCallId").asText());
                block.put("name", delta.path("toolName").asText());
                block.set("arguments", JSON.objectNode());
            }
            default -> {
                // End events and partial tool-call JSON do not alter the public snapshot here.
            }
        }
    }

    private ArrayNode messages(List<AgentMessage> messages) {
        ArrayNode values = JSON.arrayNode();
        messages.forEach(message -> values.add(message(message)));
        return values;
    }

    private ArrayNode toolResults(List<AgentMessage> results) {
        ArrayNode values = JSON.arrayNode();
        results.forEach(result -> values.add(message(result)));
        return values;
    }

    private static ObjectNode type(String type) {
        return JSON.objectNode().put("type", type);
    }
}
