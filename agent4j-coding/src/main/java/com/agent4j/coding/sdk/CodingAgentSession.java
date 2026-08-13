package com.agent4j.coding.sdk;

import com.agent4j.coding.session.SessionManager;
import com.agent4j.core.runtime.AgentConversationContext;
import com.agent4j.core.runtime.AbortController;
import com.agent4j.core.runtime.LiveAgentQueues;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class CodingAgentSession implements AgentSession {
    private final CodingAgentSessionRuntime runtime;
    private final SessionManager sessionManager;
    private AgentConversationContext conversationContext;
    private final AtomicReference<ActivePrompt> activePrompt = new AtomicReference<>();

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

    @Override public boolean isStreaming() { return activePrompt.get() != null; }
    @Override public void steer(String message) { runtime.queue(this, message, true); }
    @Override public void followUp(String message) { runtime.queue(this, message, false); }
    @Override public boolean abort(String reason) {
        ActivePrompt active = activePrompt.get();
        return active != null && active.abortController().abort(reason);
    }

    SessionManager sessionManager() {
        return sessionManager;
    }

    void refreshConversationContext(AgentConversationContext conversationContext) {
        this.conversationContext = Objects.requireNonNull(conversationContext, "conversationContext");
    }

    ActivePrompt beginPrompt(AbortController abortController, LiveAgentQueues queues) {
        ActivePrompt active = new ActivePrompt(abortController, queues);
        if (!activePrompt.compareAndSet(null, active)) {
            throw new IllegalStateException("a prompt is already active for this session");
        }
        return active;
    }

    void endPrompt(ActivePrompt active) { activePrompt.compareAndSet(active, null); }

    ActivePrompt requireActivePrompt() {
        ActivePrompt active = activePrompt.get();
        if (active == null) throw new IllegalStateException("no prompt is active for this session");
        return active;
    }

    record ActivePrompt(AbortController abortController, LiveAgentQueues queues) { }
}
