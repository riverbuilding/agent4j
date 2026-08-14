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
        @JsonSubTypes.Type(value = AgentEvent.AgentStarted.class, name = AgentEvent.AgentStarted.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.AgentEnded.class, name = AgentEvent.AgentEnded.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.TurnStarted.class, name = AgentEvent.TurnStarted.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.TurnEnded.class, name = AgentEvent.TurnEnded.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.MessageStarted.class, name = AgentEvent.MessageStarted.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.MessageUpdated.class, name = AgentEvent.MessageUpdated.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.MessageEnded.class, name = AgentEvent.MessageEnded.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.ToolExecutionStarted.class, name = AgentEvent.ToolExecutionStarted.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.ToolExecutionUpdated.class, name = AgentEvent.ToolExecutionUpdated.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.ToolExecutionEnded.class, name = AgentEvent.ToolExecutionEnded.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.QueueUpdated.class, name = AgentEvent.QueueUpdated.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.RetryStarted.class, name = AgentEvent.RetryStarted.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.RetryCompleted.class, name = AgentEvent.RetryCompleted.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.CompactionStarted.class, name = AgentEvent.CompactionStarted.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.CompactionCompleted.class, name = AgentEvent.CompactionCompleted.TYPE),
        @JsonSubTypes.Type(value = AgentEvent.AgentAborted.class, name = AgentEvent.AgentAborted.TYPE)
})
public sealed interface AgentEvent {
    String sessionId();

    Instant timestamp();

    String wireName();

    record AgentStarted(String sessionId, Instant timestamp, String turnId) implements AgentEvent {
        public static final String TYPE = "agent_start";

        @Override public String wireName() { return TYPE; }
    }

    record AgentEnded(String sessionId, Instant timestamp, String turnId, java.util.List<AgentMessage> messages, Usage usage) implements AgentEvent {
        public static final String TYPE = "agent_end";

        public AgentEnded {
            messages = messages == null ? java.util.List.of() : java.util.List.copyOf(messages);
            usage = usage == null ? Usage.zero() : usage;
        }

        @Override public String wireName() { return TYPE; }
    }

    record TurnStarted(String sessionId, Instant timestamp, String turnId) implements AgentEvent {
        public static final String TYPE = "turn_start";

        @Override public String wireName() { return TYPE; }
    }

    record TurnEnded(String sessionId, Instant timestamp, String turnId, AgentMessage message, java.util.List<AgentMessage> toolResults, Usage usage) implements AgentEvent {
        public static final String TYPE = "turn_end";

        public TurnEnded {
            toolResults = toolResults == null ? java.util.List.of() : java.util.List.copyOf(toolResults);
            usage = usage == null ? Usage.zero() : usage;
        }

        @Override public String wireName() { return TYPE; }
    }

    record MessageStarted(String sessionId, Instant timestamp, AgentMessage message) implements AgentEvent {
        public static final String TYPE = "message_start";

        @Override public String wireName() { return TYPE; }
    }

    record MessageUpdated(String sessionId, Instant timestamp, String messageId, JsonNode delta) implements AgentEvent {
        public static final String TYPE = "message_update";

        @Override public String wireName() { return TYPE; }
    }

    record MessageEnded(String sessionId, Instant timestamp, AgentMessage message) implements AgentEvent {
        public static final String TYPE = "message_end";

        @Override public String wireName() { return TYPE; }
    }

    record ToolExecutionStarted(String sessionId, Instant timestamp, ToolCall toolCall) implements AgentEvent {
        public static final String TYPE = "tool_execution_start";

        @Override public String wireName() { return TYPE; }
    }

    record ToolExecutionUpdated(String sessionId, Instant timestamp, String toolCallId, JsonNode delta) implements AgentEvent {
        public static final String TYPE = "tool_execution_update";

        @Override public String wireName() { return TYPE; }
    }

    record ToolExecutionEnded(String sessionId, Instant timestamp, ToolResult result) implements AgentEvent {
        public static final String TYPE = "tool_execution_end";

        @Override public String wireName() { return TYPE; }
    }

    record QueueUpdated(String sessionId, Instant timestamp, QueueKind queueKind, int size) implements AgentEvent {
        public static final String TYPE = "queue_updated";

        @Override public String wireName() { return TYPE; }
    }

    record RetryStarted(String sessionId, Instant timestamp, int attempt, String reason) implements AgentEvent {
        public static final String TYPE = "retry_started";

        @Override public String wireName() { return TYPE; }
    }

    record RetryCompleted(String sessionId, Instant timestamp, int attempt, boolean success) implements AgentEvent {
        public static final String TYPE = "retry_completed";

        @Override public String wireName() { return TYPE; }
    }

    record CompactionStarted(String sessionId, Instant timestamp, String reason) implements AgentEvent {
        public static final String TYPE = "compaction_started";

        @Override public String wireName() { return TYPE; }
    }

    record CompactionCompleted(String sessionId, Instant timestamp, String summaryMessageId) implements AgentEvent {
        public static final String TYPE = "compaction_completed";

        @Override public String wireName() { return TYPE; }
    }

    record AgentAborted(String sessionId, Instant timestamp, String reason) implements AgentEvent {
        public static final String TYPE = "agent_aborted";

        @Override public String wireName() { return TYPE; }
    }
}
