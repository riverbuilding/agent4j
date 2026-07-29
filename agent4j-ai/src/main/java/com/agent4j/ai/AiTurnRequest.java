package com.agent4j.ai;

import java.util.List;
import java.util.Objects;

public record AiTurnRequest(List<AiMessage> messages, List<AiToolSpec> tools) {
    public AiTurnRequest {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(tools, "tools");
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }
}
