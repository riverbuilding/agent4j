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
        @JsonSubTypes.Type(value = AgentEvent.AgentStarted.class, name = "agent_start"),
        @JsonSubTypes.Type(value = AgentEvent.AgentEnded.class, name = "agent_end"),
        @JsonSubTypes.Type(value = AgentEvent.TurnStarted.class, name = "turn_start"),
        @JsonSubTypes.Type(value = AgentEvent.TurnEnded.class, name = "turn_end"),
        @JsonSubTypes.Type(value = AgentEvent.MessageStarted.class, name = "message_start"),
        @JsonSubTypes.Type(value = AgentEvent.MessageUpdated.class, name = "message_update"),
        @JsonSubTypes.Type(value = AgentEvent.MessageEnded.class, name = "message_end"),
        @JsonSubTypes.Type(value = AgentEvent.ToolExecutionStarted.class, name = "tool_execution_start"),
        @JsonSubTypes.Type(value = AgentEvent.ToolExecutionUpdated.class, name = "tool_execution_update"),
        @JsonSubTypes.Type(value = AgentEvent.ToolExecutionEnded.class, name = "tool_execution_end"),
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

    record AgentEnded(String sessionId, Instant timestamp, String turnId, java.util.List<AgentMessage> messages, Usage usage) implements AgentEvent {
        public AgentEnded {
            messages = messages == null ? java.util.List.of() : java.util.List.copyOf(messages);
            usage = usage == null ? Usage.zero() : usage;
        }
    }

    record TurnStarted(String sessionId, Instant timestamp, String turnId) implements AgentEvent {
    }

    record TurnEnded(String sessionId, Instant timestamp, String turnId, AgentMessage message, java.util.List<AgentMessage> toolResults, Usage usage) implements AgentEvent {
        public TurnEnded {
            toolResults = toolResults == null ? java.util.List.of() : java.util.List.copyOf(toolResults);
            usage = usage == null ? Usage.zero() : usage;
        }
    }

    record MessageStarted(String sessionId, Instant timestamp, AgentMessage message) implements AgentEvent {
    }

    record MessageUpdated(String sessionId, Instant timestamp, String messageId, JsonNode delta) implements AgentEvent {
    }

    record MessageEnded(String sessionId, Instant timestamp, AgentMessage message) implements AgentEvent {
    }

    record ToolExecutionStarted(String sessionId, Instant timestamp, ToolCall toolCall) implements AgentEvent {
    }

    record ToolExecutionUpdated(String sessionId, Instant timestamp, String toolCallId, JsonNode delta) implements AgentEvent {
    }

    record ToolExecutionEnded(String sessionId, Instant timestamp, ToolResult result) implements AgentEvent {
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
