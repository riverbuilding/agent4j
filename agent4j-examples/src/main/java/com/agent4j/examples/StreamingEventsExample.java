package com.agent4j.examples;

import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CodingAgentSession;
import com.agent4j.coding.sdk.PromptResult;
import com.agent4j.core.event.EventSubscription;

/** 02-streaming-events: prints the public agent-event lifecycle around a real streamed prompt. */
public final class StreamingEventsExample {
    private StreamingEventsExample() {
    }

    public static void main(String[] args) throws Exception {
        try (LiveExampleConfiguration configuration = LiveExampleConfiguration.open()) {
            CodingAgentRuntime runtime = configuration.createRuntime();
            CodingAgentSession session = runtime.createSession(
                    configuration.sessionFile("02-streaming-events.jsonl"), configuration.workspace());
            PromptResult result;
            try (EventSubscription ignored = runtime.subscribe(event ->
                    System.out.println("event: " + event.wireName()))) {
                result = session.prompt(LiveExampleHelper.buildPromptRequest(
                        runtime.defaultModel(),
                        "Reply with exactly: lifecycle events observed.",
                        0));
            }
            LiveExampleHelper.printMessage(System.out, result);
            LiveExampleHelper.printUsage(System.out, result);
        }
    }
}
