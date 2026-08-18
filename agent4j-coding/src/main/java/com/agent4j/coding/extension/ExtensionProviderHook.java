package com.agent4j.coding.extension;

import com.agent4j.ai.AiStreamEvent;

/** Observes a provider round through redacted request data and stream events. */
public interface ExtensionProviderHook {
    default ExtensionProviderRequestMutation beforeRequest(ExtensionProviderRequest request) throws Exception {
        return ExtensionProviderRequestMutation.none();
    }
    default void onStreamEvent(AiStreamEvent event) throws Exception {
    }
    default void onCompletion(AiStreamEvent.MessageCompleted event) throws Exception {
    }
    default void onFailure(Throwable failure) throws Exception {
    }
}
