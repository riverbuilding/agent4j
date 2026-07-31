package com.agent4j.ai;

public final class AiRequestAbortedException extends RuntimeException {
    public AiRequestAbortedException() {
        super("AI request aborted");
    }
}
