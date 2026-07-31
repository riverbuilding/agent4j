package com.agent4j.ai.anthropic;

import java.util.function.Consumer;

@FunctionalInterface
public interface AnthropicTransport {
    void stream(AnthropicHttpRequest request, Consumer<String> lineSink) throws Exception;
}
