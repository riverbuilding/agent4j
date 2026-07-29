package com.agent4j.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public sealed interface AiStreamEvent {
    record MessageStarted(String messageId) implements AiStreamEvent {
        public MessageStarted {
            Objects.requireNonNull(messageId, "messageId");
        }
    }

    record TextDelta(String messageId, int contentIndex, String delta) implements AiStreamEvent {
        public TextDelta {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(delta, "delta");
        }
    }

    record ThinkingDelta(String messageId, int contentIndex, String delta) implements AiStreamEvent {
        public ThinkingDelta {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(delta, "delta");
        }
    }

    record ToolCallDelta(String messageId, int contentIndex, JsonNode delta) implements AiStreamEvent {
        public ToolCallDelta {
            Objects.requireNonNull(messageId, "messageId");
        }
    }

    record MessageCompleted(String messageId, AiAssistantMessage message) implements AiStreamEvent {
        public MessageCompleted {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(message, "message");
        }
    }
}
