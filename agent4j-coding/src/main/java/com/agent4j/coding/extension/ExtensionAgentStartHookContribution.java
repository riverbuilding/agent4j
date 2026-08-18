package com.agent4j.coding.extension;

import java.util.Objects;

/** A named agent-start hook contributed by one extension. */
public record ExtensionAgentStartHookContribution(String extensionName, String name, ExtensionAgentStartHook hook) {
    public ExtensionAgentStartHookContribution {
        Objects.requireNonNull(extensionName, "extensionName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(hook, "hook");
        if (name.isBlank()) {
            throw new IllegalArgumentException("agent-start hook name must not be blank");
        }
    }
}
