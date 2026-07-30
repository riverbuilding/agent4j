package com.agent4j.core.message;

import java.util.Objects;

public record UnknownAgentMessageView(AgentMessage envelope) implements AgentMessageView {
    public UnknownAgentMessageView {
        Objects.requireNonNull(envelope, "envelope");
    }
}
