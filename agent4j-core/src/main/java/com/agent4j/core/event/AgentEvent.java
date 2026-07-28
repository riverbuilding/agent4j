package com.agent4j.core.event;

import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.runtime.QueueKind;
import com.agent4j.core.runtime.Usage;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AgentEvent.AgentStarted.class, name = "agent_started"),
        @JsonSubTypes.Type(value = AgentEvent.AgentSettled.class, name = "agent_settled"),
        @JsonSubTypes.Type(value = AgentEvent.MessageStarted.class, name = "message_started"),
        @JsonSubTypes.Type(value = AgentEvent.MessageDelta.class, name = "message_delta"),
        @JsonSubTypes.Type(value = AgentEvent.MessageCompleted.class, name = "message_completed"),
        @JsonSubTypes.Type(value = AgentEvent.ToolExecutionStarted.class, name = "tool_execution_started"),
        @JsonSubTypes.Type(value = AgentEvent.ToolExecutionDelta.class, name = "tool_execution_delta"),
        @JsonSubTypes.Type(value = AgentEvent.ToolExecutionCompleted.class, name = "tool_execution_completed"),
        @JsonSubTypes.Type(value = AgentEvent.QueueUpdated.class, name = "queue_updated"),
        @JsonSubTypes.Type(value = AgentEvent.RetryStarted.class, name = "retry_started"),
        @JsonSubTypes.Type(value = AgentEvent.RetryCompleted.class, name = "retry_completed"),
        @JsonSubTypes.Type(value = AgentEvent.CompactionStarted.class, name = "compaction_started"),
        @JsonSubTypes.Type(value = AgentEvent.CompactionCompleted.class, name = "compaction_completed"),
        @JsonSubTypes.Type(value = AgentEvent.AgentAborted.class, name = "agent_aborted")
})
public sealed interface AgentEvent {
    String sessionId();

    Instant timestamp();

    record AgentStarted(String sessionId, Instant timestamp, String turnId) implements AgentEvent {
    }

    record AgentSettled(String sessionId, Instant timestamp, String turnId, Usage usage) implements AgentEvent {
    }

    record MessageStarted(String sessionId, Instant timestamp, AgentMessage message) implements AgentEvent {
    }

    record MessageDelta(String sessionId, Instant timestamp, String messageId, JsonNode delta) implements AgentEvent {
    }

    record MessageCompleted(String sessionId, Instant timestamp, AgentMessage message) implements AgentEvent {
    }

    record ToolExecutionStarted(String sessionId, Instant timestamp, ToolCall toolCall) implements AgentEvent {
    }

    record ToolExecutionDelta(String sessionId, Instant timestamp, String toolCallId, JsonNode delta) implements AgentEvent {
    }

    record ToolExecutionCompleted(String sessionId, Instant timestamp, ToolResult result) implements AgentEvent {
    }

    record QueueUpdated(String sessionId, Instant timestamp, QueueKind queueKind, int size) implements AgentEvent {
    }

    record RetryStarted(String sessionId, Instant timestamp, int attempt, String reason) implements AgentEvent {
    }

    record RetryCompleted(String sessionId, Instant timestamp, int attempt, boolean success) implements AgentEvent {
    }

    record CompactionStarted(String sessionId, Instant timestamp, String reason) implements AgentEvent {
    }

    record CompactionCompleted(String sessionId, Instant timestamp, String summaryMessageId) implements AgentEvent {
    }

    record AgentAborted(String sessionId, Instant timestamp, String reason) implements AgentEvent {
    }
}
