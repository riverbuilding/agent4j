package com.agent4j.coding.sdk;

import com.agent4j.coding.session.SessionEntry;
import com.agent4j.core.runtime.AgentLoopResult;

import java.util.List;
import java.util.Objects;

public record PromptResult(
        AgentSession session,
        AgentLoopResult loopResult,
        List<SessionEntry> persistedEntries
) {
    public PromptResult {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(loopResult, "loopResult");
        persistedEntries = persistedEntries == null ? List.of() : List.copyOf(persistedEntries);
    }
}
