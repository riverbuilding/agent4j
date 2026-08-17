package com.agent4j.examples;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CodingAgentSession;
import com.agent4j.coding.sdk.PromptResult;

/** 08-prompt-model-override: uses the default then a per-prompt model override in one persisted session. */
public final class PromptModelOverrideExample {
    private PromptModelOverrideExample() {
    }

    public static void main(String[] args) throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open();
        CodingAgentRuntime runtime = CodingAgentRuntime.create(configuration.toCodingAgentConfig());
        try (runtime) {
            CodingAgentSession session = runtime.createSession("08-prompt-model-override.jsonl");
            System.out.println("Default model: " + runtime.defaultModel().displayName());
            PromptResult first = session.prompt(LiveExampleHelper.buildPromptRequest(
                    "Remember the exact phrase MODEL_OVERRIDE_CONTEXT. Reply with only that phrase.",
                    0));
            LiveExampleHelper.printMessage(System.out, first);
            LiveExampleHelper.printUsage(System.out, first);

            AiModelReference overrideModel = configuration.requireSwitchModel();
            System.out.println("Prompt override: " + overrideModel.displayName());
            PromptResult second = session.prompt(LiveExampleHelper.buildPromptRequestWithModelOverride(
                    overrideModel,
                    "What exact phrase did I ask you to remember? Reply with only that phrase.",
                    0));
            LiveExampleHelper.printMessage(System.out, second);
            LiveExampleHelper.printUsage(System.out, second);
        } finally {
            runtime.cleanupOwnedFiles();
        }
    }
}
