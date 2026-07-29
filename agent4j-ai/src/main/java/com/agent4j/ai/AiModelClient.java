package com.agent4j.ai;

import java.util.function.Consumer;

public interface AiModelClient {
    void stream(AiTurnRequest request, Consumer<AiStreamEvent> sink) throws Exception;
}
