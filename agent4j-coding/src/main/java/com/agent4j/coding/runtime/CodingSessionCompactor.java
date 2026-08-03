package com.agent4j.coding.runtime;

import com.agent4j.ai.AiProviderContext;
import com.agent4j.core.compaction.CompactionReason;
import com.agent4j.core.compaction.CompactionRequest;
import com.agent4j.core.compaction.CompactionResult;
import com.agent4j.core.compaction.CompactionService;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CodingSessionCompactor {
    private final CompactionService compactionService;
    private final AgentEventBus eventBus;
    private final Clock clock;

    public CodingSessionCompactor(AgentEventBus eventBus) {
        this(new CompactionService(), eventBus, Clock.systemUTC());
    }

    public CodingSessionCompactor(CompactionService compactionService, AgentEventBus eventBus, Clock clock) {
        this.compactionService = Objects.requireNonNull(compactionService, "compactionService");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompactionResult compact(ManualCompactionRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        String sessionId = sessionId(request);
        eventBus.publish(new AgentEvent.CompactionStarted(sessionId, now(), CompactionReason.MANUAL.wireName()));
        CompactionResult result = compactionService.compact(
                new CompactionRequest(
                        sessionId,
                        CompactionReason.MANUAL,
                        request.sessionManager().activeAgentMessages(),
                        request.systemPrompt(),
                        request.config(),
                        request.focusInstructions()),
                request.selection().provider(),
                request.selection().model(),
                providerContext(request, sessionId),
                request.options());
        request.sessionManager().appendCompactionResult(result);
        eventBus.publish(new AgentEvent.CompactionCompleted(
                sessionId,
                now(),
                result.optionalSummaryMessage().map(com.agent4j.core.message.AgentMessage::id).orElse(null)));
        return result;
    }

    private AiProviderContext providerContext(ManualCompactionRequest request, String sessionId) {
        return new AiProviderContext(
                Optional.of(sessionId),
                Optional.empty(),
                request.optionalCwd(),
                request.auth(),
                request.auth().environment(),
                Map.of("compactionReason", CompactionReason.MANUAL.wireName()));
    }

    private String sessionId(ManualCompactionRequest request) {
        return request.sessionManager().document()
                .header()
                .header()
                .map(com.agent4j.coding.session.SessionHeader::id)
                .filter(id -> !id.isBlank())
                .orElse("default");
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
