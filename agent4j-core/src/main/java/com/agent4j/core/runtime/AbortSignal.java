package com.agent4j.core.runtime;

import java.util.Optional;

public interface AbortSignal {
    boolean aborted();

    Optional<String> reason();

    default void throwIfAborted() {
        if (aborted()) {
            throw new AgentAbortException(reason().orElse("aborted"));
        }
    }
}
