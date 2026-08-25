package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.sdk.PromptRequest;
import com.agent4j.core.runtime.AbortSignal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Creates CLI prompts with the shared process-mode defaults. */
final class CliPromptRequestFactory {
    static final int DEFAULT_MAX_TOOL_ROUNDS = 20;

    private CliPromptRequestFactory() {
    }

    static PromptRequest create(String prompt, Optional<AiModelReference> model, Optional<AbortSignal> abortSignal) {
        return new PromptRequest(
                Objects.requireNonNull(prompt, "prompt"),
                model == null ? Optional.empty() : model,
                DEFAULT_MAX_TOOL_ROUNDS,
                0,
                Optional.empty(),
                Optional.empty(),
                null,
                java.util.Map.of(),
                List.of(),
                List.of(),
                null,
                null,
                abortSignal == null ? Optional.empty() : abortSignal,
                Optional.empty());
    }
}
