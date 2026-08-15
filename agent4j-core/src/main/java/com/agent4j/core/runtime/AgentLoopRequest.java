package com.agent4j.core.runtime;

import com.agent4j.core.message.AgentMessage;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Turn context, immutable execution options, and live queue state for one agent-loop run. */
public record AgentLoopRequest(
        String sessionId,
        String turnId,
        String parentMessageId,
        List<AgentMessage> messages,
        Path cwd,
        Clock clock,
        AbortSignal abortSignal,
        AgentLoopOptions options,
        LiveAgentQueues liveQueues
) {
    public AgentLoopRequest(
            String sessionId,
            String turnId,
            String parentMessageId,
            List<AgentMessage> messages,
            Path cwd,
            Clock clock,
            AbortSignal abortSignal,
            AgentLoopOptions options
    ) {
        this(sessionId, turnId, parentMessageId, messages, cwd, clock, abortSignal, options,
                new LiveAgentQueues(List.of(), List.of()));
    }

    public AgentLoopRequest {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(turnId, "turnId");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(abortSignal, "abortSignal");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(liveQueues, "liveQueues");
        messages = List.copyOf(messages);
    }

    public Map<String, Object> toolAttributes() {
        return options.toolAttributes();
    }

    public String systemPrompt() {
        return options.systemPrompt();
    }

    public int maxToolRounds() {
        return options.maxToolRounds();
    }

    public int maxModelRetries() {
        return options.maxModelRetries();
    }

    public Optional<Duration> modelTimeout() {
        return options.modelTimeout();
    }

    public ToolExecutionMode toolExecutionMode() {
        return options.toolExecutionMode();
    }

    public List<AgentMessage> promptMessages() {
        return options.promptMessages();
    }

    public QueueMode steeringMode() {
        return options.steeringMode();
    }

    public QueueMode followUpMode() {
        return options.followUpMode();
    }

    public com.agent4j.core.compaction.CompactionConfig compactionConfig() {
        return options.compactionConfig();
    }
}
