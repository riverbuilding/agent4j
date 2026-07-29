package com.agent4j.core.runtime;

import com.agent4j.ai.AiMessage;
import com.agent4j.core.message.AgentMessage;

import java.util.List;

@FunctionalInterface
public interface AgentMessageConverter {
    List<AiMessage> convertToLlm(List<AgentMessage> messages);
}
