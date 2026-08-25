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

    static PromptRequest buildPromptRequest(String prompt, int maxToolRounds) {
        return buildPromptRequest(prompt, maxToolRounds, Optional.empty());
    }

    static PromptRequest buildPromptRequestWithModelOverride(
            AiModelReference model,
            String prompt,
            int maxToolRounds
    ) {
        return buildPromptRequest(prompt, maxToolRounds, Optional.empty(), Optional.of(model));
    }

    static PromptRequest buildPromptRequest(
            String prompt,
            int maxToolRounds,
            String systemPrompt
    ) {
        return buildPromptRequest(prompt, maxToolRounds, Optional.of(systemPrompt));
    }

    private static PromptRequest buildPromptRequest(
            String prompt,
            int maxToolRounds,
            Optional<String> systemPrompt
    ) {
        return buildPromptRequest(prompt, maxToolRounds, systemPrompt, Optional.empty());
    }

    private static PromptRequest buildPromptRequest(
            String prompt,
            int maxToolRounds,
            Optional<String> systemPrompt,
            Optional<AiModelReference> model
    ) {
        return new PromptRequest(
                prompt,
                model,
                maxToolRounds,
                0,
                Optional.empty(),
                Optional.empty(),
                null,
                java.util.Map.of(),
                java.util.List.of(),
                java.util.List.of(),
                null,
                null,
                Optional.empty(),
                systemPrompt);
    }

    static PromptRequest buildToolRequiredPromptRequest(
            String prompt,
            int maxToolRounds,
            String systemPrompt
    ) {
        return new PromptRequest(
                prompt,
                Optional.empty(),
                maxToolRounds,
                0,
                Optional.empty(),
                Optional.of("required"),
                null,
                java.util.Map.of(),
                java.util.List.of(),
                java.util.List.of(),
                null,
                null,
                Optional.empty(),
                Optional.of(systemPrompt));
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
