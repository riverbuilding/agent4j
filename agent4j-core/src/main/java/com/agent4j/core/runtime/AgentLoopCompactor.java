package com.agent4j.core.runtime;

import com.agent4j.ai.AiAbortSignal;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderContext;
import com.agent4j.ai.AiResolvedAuth;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.core.compaction.CompactionPlan;
import com.agent4j.core.compaction.CompactionReason;
import com.agent4j.core.compaction.CompactionRequest;
import com.agent4j.core.compaction.CompactionResult;
import com.agent4j.core.compaction.CompactionService;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;

import java.util.Map;
import java.util.Optional;

/** Applies threshold and overflow compaction for provider-backed agent loops. */
final class AgentLoopCompactor {
    private final CompactionService service;
    private final AiProvider provider;
    private final AiModel model;
    private final AiResolvedAuth auth;
    private final AgentEventBus eventBus;

    AgentLoopCompactor(CompactionService service, AiProvider provider, AiModel model, AiResolvedAuth auth, AgentEventBus eventBus) {
        this.service = service;
        this.provider = provider;
        this.model = model;
        this.auth = auth;
        this.eventBus = eventBus;
    }

    void compactForThreshold(AgentLoopRequest request, AgentConversationContext conversation) throws Exception {
        if (request.compactionConfig().enabled()) compact(request, conversation, CompactionReason.THRESHOLD);
    }

    void compactForOverflow(AgentLoopRequest request, AgentConversationContext conversation, Exception failure) throws Exception {
        if (!isContextOverflow(failure) || !request.compactionConfig().enabled()
                || !request.compactionConfig().overflowRetryEnabled()
                || !compact(request, conversation, CompactionReason.OVERFLOW)) {
            throw failure;
        }
    }

    private boolean compact(AgentLoopRequest request, AgentConversationContext conversation, CompactionReason reason) throws Exception {
        if (service == null || provider == null || model == null) return false;
        CompactionRequest compactionRequest = new CompactionRequest(request.sessionId(), reason,
                conversation.transcriptMessages(), request.systemPrompt(), request.compactionConfig(), null);
        CompactionPlan plan = service.plan(compactionRequest, model);
        if (!plan.compact()) return false;
        eventBus.publish(new AgentEvent.CompactionStarted(request.sessionId(), request.clock().instant(), reason.wireName()));
        CompactionResult result = service.compact(compactionRequest, provider, model, context(request), options(request));
        if (!result.compacted()) {
            eventBus.publish(new AgentEvent.CompactionCompleted(request.sessionId(), request.clock().instant(), null));
            return false;
        }
        conversation.replaceTranscript(result.compactedMessages());
        conversation.recordGenerated(result.summaryMessage());
        eventBus.publish(new AgentEvent.MessageStarted(request.sessionId(), request.clock().instant(), result.summaryMessage()));
        eventBus.publish(new AgentEvent.MessageEnded(request.sessionId(), request.clock().instant(), result.summaryMessage()));
        eventBus.publish(new AgentEvent.CompactionCompleted(request.sessionId(), request.clock().instant(), result.summaryMessage().id()));
        return true;
    }

    private AiProviderContext context(AgentLoopRequest request) {
        Map<String, Object> attributes = request.parentMessageId() == null || request.parentMessageId().isBlank()
                ? Map.of("maxToolRounds", request.maxToolRounds())
                : Map.of("parentMessageId", request.parentMessageId(), "maxToolRounds", request.maxToolRounds());
        return new AiProviderContext(Optional.of(request.sessionId()), Optional.of(request.turnId()), Optional.of(request.cwd()),
                auth, Map.of(), attributes);
    }

    private static AiStreamOptions options(AgentLoopRequest request) {
        return new AiStreamOptions(new AiAbortSignal() {
            @Override public boolean aborted() { return request.abortSignal().aborted(); }
            @Override public void throwIfAborted() { request.abortSignal().throwIfAborted(); }
        }, request.modelTimeout(), 0, Map.of(), Map.of());
    }

    static boolean isContextOverflow(Exception error) {
        String message = error.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("context_length_exceeded") || lower.contains("context length")
                || lower.contains("maximum context") || lower.contains("token limit")
                || lower.contains("too many tokens") || lower.contains("exceeds the model's maximum")
                || lower.contains("reduce the length");
    }
}
