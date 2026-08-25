package com.agent4j.examples;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CodingAgentSession;
import com.agent4j.coding.sdk.PromptResult;

/** 07-model-switching: changes an application's selected model between turns in one persisted session. */
public final class ModelSwitchingExample {
    private ModelSwitchingExample() {
    }

    public static void main(String[] args) throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open();
        CodingAgentRuntime runtime = CodingAgentRuntime.create(configuration.toCodingAgentConfig());
        try (runtime) {
            CodingAgentSession session = runtime.createSession("07-model-switching.jsonl");
            AiModelReference selectedModel = runtime.defaultModel();
            System.out.println("Selected model: " + selectedModel.displayName());
            PromptResult first = session.prompt(LiveExampleHelper.buildPromptRequestWithModelOverride(
                    selectedModel,
                    "Reply with exactly: first selected model completed.",
                    0));
            LiveExampleHelper.printMessage(System.out, first);
            LiveExampleHelper.printUsage(System.out, first);

            selectedModel = configuration.requireSwitchModel();
            System.out.println("Selected model: " + selectedModel.displayName());
            PromptResult second = session.prompt(LiveExampleHelper.buildPromptRequestWithModelOverride(
                    selectedModel,
                    "Reply with exactly: switched selected model completed.",
                    0));
            LiveExampleHelper.printMessage(System.out, second);
            LiveExampleHelper.printUsage(System.out, second);
        } finally {
            runtime.cleanupOwnedFiles();
        }
    }
}
