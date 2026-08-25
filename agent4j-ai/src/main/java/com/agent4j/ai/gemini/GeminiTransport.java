package com.agent4j.ai.gemini;

import java.util.function.Consumer;

public interface GeminiTransport {
    void stream(GeminiHttpRequest request, Consumer<String> lineSink) throws Exception;
}
