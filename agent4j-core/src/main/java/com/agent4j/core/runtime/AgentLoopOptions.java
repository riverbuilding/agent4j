package com.agent4j.core.runtime;

import com.agent4j.core.compaction.CompactionConfig;
import com.agent4j.core.message.AgentMessage;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable execution settings for one agent-loop turn. */
public record AgentLoopOptions(
        Map<String, Object> toolAttributes,
        String systemPrompt,
        int maxToolRounds,
        int maxModelRetries,
        Optional<Duration> modelTimeout,
        ToolExecutionMode toolExecutionMode,
        List<AgentMessage> promptMessages,
        QueueMode steeringMode,
        QueueMode followUpMode,
        CompactionConfig compactionConfig
) {
    public AgentLoopOptions {
        if (maxToolRounds < 0) {
            throw new IllegalArgumentException("maxToolRounds must be non-negative");
        }
        if (maxModelRetries < 0) {
            throw new IllegalArgumentException("maxModelRetries must be non-negative");
        }
        toolAttributes = toolAttributes == null ? Map.of() : Map.copyOf(toolAttributes);
        systemPrompt = systemPrompt == null || systemPrompt.isBlank() ? null : systemPrompt;
        modelTimeout = modelTimeout == null ? Optional.empty() : modelTimeout;
        modelTimeout.ifPresent(timeout -> {
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("modelTimeout must be positive");
            }
        });
        toolExecutionMode = toolExecutionMode == null ? ToolExecutionMode.PARALLEL : toolExecutionMode;
        promptMessages = promptMessages == null ? List.of() : List.copyOf(promptMessages);
        steeringMode = steeringMode == null ? QueueMode.ONE_AT_A_TIME : steeringMode;
        followUpMode = followUpMode == null ? QueueMode.ONE_AT_A_TIME : followUpMode;
        compactionConfig = compactionConfig == null
                ? CompactionConfig.builder().enabled(false).build()
                : compactionConfig;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .toolAttributes(toolAttributes)
                .systemPrompt(systemPrompt)
                .maxToolRounds(maxToolRounds)
                .maxModelRetries(maxModelRetries)
                .modelTimeout(modelTimeout)
                .toolExecutionMode(toolExecutionMode)
                .promptMessages(promptMessages)
                .steeringMode(steeringMode)
                .followUpMode(followUpMode)
                .compactionConfig(compactionConfig);
    }

    public static final class Builder {
        private Map<String, Object> toolAttributes = Map.of();
        private String systemPrompt;
        private int maxToolRounds;
        private int maxModelRetries;
        private Optional<Duration> modelTimeout = Optional.empty();
        private ToolExecutionMode toolExecutionMode = ToolExecutionMode.PARALLEL;
        private List<AgentMessage> promptMessages = List.of();
        private QueueMode steeringMode = QueueMode.ONE_AT_A_TIME;
        private QueueMode followUpMode = QueueMode.ONE_AT_A_TIME;
        private CompactionConfig compactionConfig = CompactionConfig.builder().enabled(false).build();

        public Builder toolAttributes(Map<String, Object> toolAttributes) {
            this.toolAttributes = Objects.requireNonNull(toolAttributes, "toolAttributes");
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder maxToolRounds(int maxToolRounds) {
            this.maxToolRounds = maxToolRounds;
            return this;
        }

        public Builder maxModelRetries(int maxModelRetries) {
            this.maxModelRetries = maxModelRetries;
            return this;
        }

        public Builder modelTimeout(Optional<Duration> modelTimeout) {
            this.modelTimeout = Objects.requireNonNull(modelTimeout, "modelTimeout");
            return this;
        }

        public Builder toolExecutionMode(ToolExecutionMode toolExecutionMode) {
            this.toolExecutionMode = Objects.requireNonNull(toolExecutionMode, "toolExecutionMode");
            return this;
        }

        public Builder promptMessages(List<AgentMessage> promptMessages) {
            this.promptMessages = Objects.requireNonNull(promptMessages, "promptMessages");
            return this;
        }

        public Builder steeringMode(QueueMode steeringMode) {
            this.steeringMode = Objects.requireNonNull(steeringMode, "steeringMode");
            return this;
        }

        public Builder followUpMode(QueueMode followUpMode) {
            this.followUpMode = Objects.requireNonNull(followUpMode, "followUpMode");
            return this;
        }

        public Builder compactionConfig(CompactionConfig compactionConfig) {
            this.compactionConfig = Objects.requireNonNull(compactionConfig, "compactionConfig");
            return this;
        }

        public AgentLoopOptions build() {
            return new AgentLoopOptions(
                    toolAttributes,
                    systemPrompt,
                    maxToolRounds,
                    maxModelRetries,
                    modelTimeout,
                    toolExecutionMode,
                    promptMessages,
                    steeringMode,
                    followUpMode,
                    compactionConfig);
        }
    }
}
