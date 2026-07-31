package com.agent4j.ai;

@FunctionalInterface
public interface AiProviderRequestHook {
    AiProviderRequest apply(AiProviderRequest request) throws Exception;

    static AiProviderRequestHook identity() {
        return request -> request;
    }
}
