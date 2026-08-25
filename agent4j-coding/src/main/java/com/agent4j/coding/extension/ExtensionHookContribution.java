package com.agent4j.coding.extension;

import com.agent4j.core.tool.ToolExecutionHook;

import java.util.Objects;

/** A named tool-execution hook contributed by one extension. */
public record ExtensionHookContribution(String extensionName, String name, ToolExecutionHook hook) {
    public ExtensionHookContribution {
        Objects.requireNonNull(extensionName, "extensionName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(hook, "hook");
        if (name.isBlank()) {
            throw new IllegalArgumentException("hook name must not be blank");
        }
    }
}
