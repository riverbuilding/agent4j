package com.agent4j.core.message;

import java.util.List;
import java.util.Objects;

public record UserAgentMessageView(AgentMessage envelope) implements AgentMessageView {
    public UserAgentMessageView {
        Objects.requireNonNull(envelope, "envelope");
    }

    public List<ContentBlock> contentBlocks() {
        return envelope.contentBlocks();
    }

    public String text() {
        return envelope.textContent();
    }
}
