package com.agent4j.coding.extension;

import java.util.Objects;

/** A named model-context transform hook contributed by one extension. */
public record ExtensionContextTransformHookContribution(
        String extensionName,
        String name,
        ExtensionContextTransformHook hook
) {
    public ExtensionContextTransformHookContribution {
        Objects.requireNonNull(extensionName, "extensionName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(hook, "hook");
        if (name.isBlank()) {
            throw new IllegalArgumentException("context-transform hook name must not be blank");
        }
    }
}
