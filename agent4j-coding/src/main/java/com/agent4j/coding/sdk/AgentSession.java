package com.agent4j.coding.sdk;

import com.agent4j.core.runtime.AgentConversationContext;

/**
 * User-facing handle for one persisted coding-agent conversation.
 */
public interface AgentSession {
    AgentSessionInfo info();

    AgentConversationContext conversationContext();

    PromptResult prompt(PromptRequest request) throws Exception;

    default boolean isStreaming() {
        return false;
    }

    default void steer(String message) {
        throw new IllegalStateException("live steering is not supported by this session");
    }

    default void followUp(String message) {
        throw new IllegalStateException("live follow-up is not supported by this session");
    }

    default boolean abort(String reason) {
        return false;
    }

    default String id() {
        return info().id();
    }

    default java.nio.file.Path sessionFile() {
        return info().sessionFile();
    }

    default java.nio.file.Path cwd() {
        return info().cwd();
    }

    default String activeEntryId() {
        return info().activeEntryId();
    }
}
