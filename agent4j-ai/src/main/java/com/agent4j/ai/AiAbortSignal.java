package com.agent4j.ai;

public interface AiAbortSignal {
    boolean aborted();

    default void throwIfAborted() {
        if (aborted()) {
            throw new AiRequestAbortedException();
        }
    }

    static AiAbortSignal none() {
        return () -> false;
    }
}
