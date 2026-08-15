package com.agent4j.examples;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.PromptRequest;
import com.agent4j.coding.sdk.PromptResult;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.EventSubscription;
import com.agent4j.core.runtime.Usage;

import java.io.PrintStream;
import java.util.Optional;

final class LiveExampleHelper {
    private LiveExampleHelper() {
    }

    static PromptRequest buildPromptRequest(AiModelReference model, String prompt, int maxToolRounds) {
        return new PromptRequest(
                prompt,
                Optional.of(model),
                maxToolRounds,
                0,
                Optional.empty(),
                null,
                java.util.Map.of(),
                java.util.List.of(),
                java.util.List.of(),
                null,
                null,
                Optional.empty());
    }

    static EventSubscription streamAssistantText(CodingAgentRuntime runtime, PrintStream output) {
        return runtime.subscribe(event -> {
            if (event instanceof AgentEvent.MessageUpdated updated
                    && "text_delta".equals(updated.delta().path("type").asText())) {
                output.print(updated.delta().path("delta").asText());
                output.flush();
            }
        });
    }

    static void printUsage(PrintStream output, PromptResult result) {
        Usage usage = result.loopResult().usage();
        output.printf("%nUsage: input=%d, output=%d, reasoning=%d, total=%d%n",
                usage.inputTokens(), usage.outputTokens(), usage.reasoningTokens(), usage.totalTokens());
    }

    static void printMessage(PrintStream output, PromptResult result) {
        result.loopResult().assistantMessages().stream()
                .map(message -> message.textContent())
                .filter(message -> !message.isBlank())
                .reduce((ignored, message) -> message)
                .ifPresent(message -> output.printf("%nAssistant: %s%n", message));
    }

}
