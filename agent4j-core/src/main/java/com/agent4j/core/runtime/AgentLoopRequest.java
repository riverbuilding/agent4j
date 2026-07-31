package com.agent4j.core.runtime;

import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.UserAgentMessageView;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AgentLoopRequest(
        String sessionId,
        String turnId,
        String parentMessageId,
        List<AgentMessage> messages,
        Path cwd,
        Clock clock,
        AbortSignal abortSignal,
        Map<String, Object> toolAttributes,
        String systemPrompt,
        int maxToolRounds,
        int maxModelRetries,
        ToolExecutionMode toolExecutionMode,
        List<AgentMessage> promptMessages,
        List<AgentMessage> steeringMessages,
        List<AgentMessage> followUpMessages,
        QueueMode steeringMode,
        QueueMode followUpMode
) {
    public AgentLoopRequest(
            String sessionId,
            String turnId,
            String parentMessageId,
            List<AgentMessage> messages,
            Path cwd,
            Clock clock,
            AbortSignal abortSignal,
            Map<String, Object> toolAttributes,
            int maxToolRounds
    ) {
        this(
                sessionId,
                turnId,
                parentMessageId,
                messages,
                cwd,
                clock,
                abortSignal,
                toolAttributes,
                null,
                maxToolRounds,
                0,
                ToolExecutionMode.PARALLEL,
                inferPromptMessages(parentMessageId, messages),
                List.of(),
                List.of(),
                QueueMode.ONE_AT_A_TIME,
                QueueMode.ONE_AT_A_TIME);
    }

    public AgentLoopRequest(
            String sessionId,
            String turnId,
            String parentMessageId,
            List<AgentMessage> messages,
            Path cwd,
            Clock clock,
            AbortSignal abortSignal,
            Map<String, Object> toolAttributes,
            String systemPrompt,
            int maxToolRounds
    ) {
        this(
                sessionId,
                turnId,
                parentMessageId,
                messages,
                cwd,
                clock,
                abortSignal,
                toolAttributes,
                systemPrompt,
                maxToolRounds,
                0,
                ToolExecutionMode.PARALLEL,
                inferPromptMessages(parentMessageId, messages),
                List.of(),
                List.of(),
                QueueMode.ONE_AT_A_TIME,
                QueueMode.ONE_AT_A_TIME);
    }

    public AgentLoopRequest(
            String sessionId,
            String turnId,
            String parentMessageId,
            List<AgentMessage> messages,
            Path cwd,
            Clock clock,
            AbortSignal abortSignal,
            Map<String, Object> toolAttributes,
            int maxToolRounds,
            int maxModelRetries,
            List<AgentMessage> promptMessages,
            List<AgentMessage> steeringMessages,
            List<AgentMessage> followUpMessages,
            QueueMode steeringMode,
            QueueMode followUpMode
    ) {
        this(
                sessionId,
                turnId,
                parentMessageId,
                messages,
                cwd,
                clock,
                abortSignal,
                toolAttributes,
                null,
                maxToolRounds,
                maxModelRetries,
                ToolExecutionMode.PARALLEL,
                promptMessages,
                steeringMessages,
                followUpMessages,
                steeringMode,
                followUpMode);
    }

    public AgentLoopRequest(
            String sessionId,
            String turnId,
            String parentMessageId,
            List<AgentMessage> messages,
            Path cwd,
            Clock clock,
            AbortSignal abortSignal,
            Map<String, Object> toolAttributes,
            String systemPrompt,
            int maxToolRounds,
            int maxModelRetries,
            List<AgentMessage> promptMessages,
            List<AgentMessage> steeringMessages,
            List<AgentMessage> followUpMessages,
            QueueMode steeringMode,
            QueueMode followUpMode
    ) {
        this(
                sessionId,
                turnId,
                parentMessageId,
                messages,
                cwd,
                clock,
                abortSignal,
                toolAttributes,
                systemPrompt,
                maxToolRounds,
                maxModelRetries,
                ToolExecutionMode.PARALLEL,
                promptMessages,
                steeringMessages,
                followUpMessages,
                steeringMode,
                followUpMode);
    }

    public AgentLoopRequest {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(turnId, "turnId");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(abortSignal, "abortSignal");
        Objects.requireNonNull(toolExecutionMode, "toolExecutionMode");
        Objects.requireNonNull(steeringMode, "steeringMode");
        Objects.requireNonNull(followUpMode, "followUpMode");
        if (maxToolRounds < 0) {
            throw new IllegalArgumentException("maxToolRounds must be non-negative");
        }
        if (maxModelRetries < 0) {
            throw new IllegalArgumentException("maxModelRetries must be non-negative");
        }
        messages = List.copyOf(messages);
        toolAttributes = toolAttributes == null ? Map.of() : Map.copyOf(toolAttributes);
        systemPrompt = systemPrompt == null || systemPrompt.isBlank() ? null : systemPrompt;
        promptMessages = promptMessages == null ? List.of() : List.copyOf(promptMessages);
        steeringMessages = steeringMessages == null ? List.of() : List.copyOf(steeringMessages);
        followUpMessages = followUpMessages == null ? List.of() : List.copyOf(followUpMessages);
    }

    private static List<AgentMessage> inferPromptMessages(String parentMessageId, List<AgentMessage> messages) {
        if (messages == null || parentMessageId == null) {
            return List.of();
        }
        return messages.stream()
                .filter(message -> parentMessageId.equals(message.id()))
                .filter(message -> message.view() instanceof UserAgentMessageView)
                .findFirst()
                .map(List::of)
                .orElseGet(List::of);
    }
}
