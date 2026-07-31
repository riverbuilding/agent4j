package com.agent4j.ai.openai;

import java.util.function.Consumer;

@FunctionalInterface
public interface OpenAiTransport {
    void stream(OpenAiHttpRequest request, Consumer<String> lineSink) throws Exception;
}
