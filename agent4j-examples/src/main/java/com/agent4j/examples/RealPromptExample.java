package com.agent4j.examples;

import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CodingAgentSession;
import com.agent4j.coding.sdk.PromptResult;
import com.agent4j.core.event.EventSubscription;

/** 01-real-prompt: sends one real prompt, renders streamed text, then reports usage. */
public final class RealPromptExample {
    private RealPromptExample() {
    }

    public static void main(String[] args) throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open();
        CodingAgentRuntime runtime = CodingAgentRuntime.create(configuration.toCodingAgentConfig());
        try (runtime) {
            CodingAgentSession session = runtime.createSession("01-real-prompt.jsonl");
            System.out.println("Assistant: ");
            PromptResult result;
            try (EventSubscription ignored = LiveExampleHelper.streamAssistantText(runtime, System.out)) {
                result = session.prompt(LiveExampleHelper.buildPromptRequest(
                        "Reply with exactly one short sentence that says live streaming is working.",
                        0));
            }
            LiveExampleHelper.printUsage(System.out, result);
        } finally {
            runtime.cleanupOwnedFiles();
        }
    }
}
