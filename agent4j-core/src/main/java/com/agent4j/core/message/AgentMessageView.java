package com.agent4j.core.message;

public sealed interface AgentMessageView permits
        UserAgentMessageView,
        AssistantAgentMessageView,
        ToolResultAgentMessageView,
        CustomAgentMessageView,
        UnknownAgentMessageView {
    AgentMessage envelope();

    default AgentMessageRole role() {
        return envelope().role();
    }
}
