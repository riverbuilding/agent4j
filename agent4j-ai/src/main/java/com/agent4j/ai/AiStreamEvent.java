package com.agent4j.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public sealed interface AiStreamEvent {
    record MessageStarted(String messageId) implements AiStreamEvent {
        public MessageStarted {
            Objects.requireNonNull(messageId, "messageId");
        }
    }

    record MessageErrored(String messageId, String error) implements AiStreamEvent {
        public MessageErrored {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(error, "error");
        }
    }

    record TextStarted(String messageId, int contentIndex) implements AiStreamEvent {
        public TextStarted {
            Objects.requireNonNull(messageId, "messageId");
        }
    }

    record TextDelta(String messageId, int contentIndex, String delta) implements AiStreamEvent {
        public TextDelta {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(delta, "delta");
        }
    }

    record TextEnded(String messageId, int contentIndex) implements AiStreamEvent {
        public TextEnded {
            Objects.requireNonNull(messageId, "messageId");
        }
    }

    record ThinkingStarted(String messageId, int contentIndex) implements AiStreamEvent {
        public ThinkingStarted {
            Objects.requireNonNull(messageId, "messageId");
        }
    }

    record ThinkingDelta(String messageId, int contentIndex, String delta) implements AiStreamEvent {
        public ThinkingDelta {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(delta, "delta");
        }
    }

    record ThinkingEnded(String messageId, int contentIndex) implements AiStreamEvent {
        public ThinkingEnded {
            Objects.requireNonNull(messageId, "messageId");
        }
    }

    record ToolCallStarted(String messageId, int contentIndex, String toolCallId, String toolName) implements AiStreamEvent {
        public ToolCallStarted {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(toolCallId, "toolCallId");
            Objects.requireNonNull(toolName, "toolName");
        }
    }

    record ToolCallDelta(String messageId, int contentIndex, JsonNode delta) implements AiStreamEvent {
        public ToolCallDelta {
            Objects.requireNonNull(messageId, "messageId");
        }
    }

    record ToolCallEnded(String messageId, int contentIndex, String toolCallId) implements AiStreamEvent {
        public ToolCallEnded {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(toolCallId, "toolCallId");
        }
    }

    record MessageCompleted(String messageId, AiAssistantMessage message) implements AiStreamEvent {
        public MessageCompleted {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(message, "message");
        }
    }
}
