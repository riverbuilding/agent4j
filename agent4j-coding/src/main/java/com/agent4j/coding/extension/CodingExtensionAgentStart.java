package com.agent4j.coding.extension;

import java.util.Objects;

/** The prompt and assembled system prompt visible to a {@code before_agent_start} lifecycle method. */
public record CodingExtensionAgentStart(String prompt, String systemPrompt) {
    public CodingExtensionAgentStart {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
    }
}
