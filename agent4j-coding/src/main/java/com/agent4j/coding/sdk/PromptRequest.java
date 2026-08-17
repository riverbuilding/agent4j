package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModelReference;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.runtime.AbortSignal;
import com.agent4j.core.runtime.QueueMode;
import com.agent4j.core.runtime.ToolExecutionMode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record PromptRequest(
        String prompt,
        Optional<AiModelReference> model,
        int maxToolRounds,
        int maxModelRetries,
        Optional<Duration> modelTimeout,
        ToolExecutionMode toolExecutionMode,
        Map<String, Object> toolAttributes,
        List<AgentMessage> steeringMessages,
        List<AgentMessage> followUpMessages,
        QueueMode steeringMode,
        QueueMode followUpMode,
        Optional<AbortSignal> abortSignal,
        Optional<String> systemPrompt
) {
    public PromptRequest(String prompt) {
        this(
                prompt,
                Optional.empty(),
                0,
                0,
                Optional.empty(),
                ToolExecutionMode.PARALLEL,
                Map.of(),
                List.of(),
                List.of(),
                QueueMode.ONE_AT_A_TIME,
                QueueMode.ONE_AT_A_TIME,
                Optional.empty(),
                Optional.empty());
    }

    public PromptRequest(
            String prompt,
            Optional<AiModelReference> model,
            int maxToolRounds,
            int maxModelRetries,
            Optional<Duration> modelTimeout,
            ToolExecutionMode toolExecutionMode,
            Map<String, Object> toolAttributes,
            List<AgentMessage> steeringMessages,
            List<AgentMessage> followUpMessages,
            QueueMode steeringMode,
            QueueMode followUpMode,
            Optional<AbortSignal> abortSignal
    ) {
        this(
                prompt,
                model,
                maxToolRounds,
                maxModelRetries,
                modelTimeout,
                toolExecutionMode,
                toolAttributes,
                steeringMessages,
                followUpMessages,
                steeringMode,
                followUpMode,
                abortSignal,
                Optional.empty());
    }

    public PromptRequest {
        Objects.requireNonNull(prompt, "prompt");
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        model = model == null ? Optional.empty() : model;
        if (maxToolRounds < 0) {
            throw new IllegalArgumentException("maxToolRounds must be non-negative");
        }
        if (maxModelRetries < 0) {
            throw new IllegalArgumentException("maxModelRetries must be non-negative");
        }
        modelTimeout = modelTimeout == null ? Optional.empty() : modelTimeout;
        modelTimeout.ifPresent(timeout -> {
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("modelTimeout must be positive");
            }
        });
        toolExecutionMode = toolExecutionMode == null ? ToolExecutionMode.PARALLEL : toolExecutionMode;
        toolAttributes = toolAttributes == null ? Map.of() : Map.copyOf(toolAttributes);
        steeringMessages = steeringMessages == null ? List.of() : List.copyOf(steeringMessages);
        followUpMessages = followUpMessages == null ? List.of() : List.copyOf(followUpMessages);
        steeringMode = steeringMode == null ? QueueMode.ONE_AT_A_TIME : steeringMode;
        followUpMode = followUpMode == null ? QueueMode.ONE_AT_A_TIME : followUpMode;
        abortSignal = abortSignal == null ? Optional.empty() : abortSignal;
        systemPrompt = systemPrompt == null ? Optional.empty() : systemPrompt.map(String::strip).filter(value -> !value.isBlank());
    }
}
