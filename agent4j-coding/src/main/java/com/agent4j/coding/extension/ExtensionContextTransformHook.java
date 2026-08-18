package com.agent4j.coding.extension;

import com.agent4j.core.message.AgentMessage;

import java.util.List;

/** Transforms ephemeral model input without changing persisted session history. */
@FunctionalInterface
public interface ExtensionContextTransformHook {
    List<AgentMessage> transformContext(List<AgentMessage> messages, ExtensionContext context) throws Exception;
}
