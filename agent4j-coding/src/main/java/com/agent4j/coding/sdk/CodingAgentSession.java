package com.agent4j.coding.sdk;

import com.agent4j.coding.session.SessionManager;
import com.agent4j.core.runtime.AgentConversationContext;

import java.util.Objects;

public final class CodingAgentSession implements AgentSession {
    private final CodingAgentSessionRuntime runtime;
    private final SessionManager sessionManager;
    private AgentConversationContext conversationContext;

    CodingAgentSession(
            CodingAgentSessionRuntime runtime,
            SessionManager sessionManager,
            AgentConversationContext conversationContext
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.conversationContext = Objects.requireNonNull(conversationContext, "conversationContext");
    }

    @Override
    public AgentSessionInfo info() {
        return runtime.sessionInfo(sessionManager);
    }

    @Override
    public AgentConversationContext conversationContext() {
        return conversationContext;
    }

    @Override
    public PromptResult prompt(PromptRequest request) throws Exception {
        PromptResult result = runtime.prompt(this, request);
        refreshConversationContext(new AgentConversationContext(
                sessionManager.activeAgentMessages(),
                result.loopResult().messages()));
        return result;
    }

    SessionManager sessionManager() {
        return sessionManager;
    }

    void refreshConversationContext(AgentConversationContext conversationContext) {
        this.conversationContext = Objects.requireNonNull(conversationContext, "conversationContext");
    }
}
