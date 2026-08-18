package com.agent4j.coding.extension;

import java.util.Objects;

/** A named lifecycle listener contributed by one extension. */
public record ExtensionLifecycleListenerContribution(
        String extensionName,
        String name,
        ExtensionLifecycleListener listener
) {
    public ExtensionLifecycleListenerContribution {
        Objects.requireNonNull(extensionName, "extensionName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(listener, "listener");
        if (name.isBlank()) {
            throw new IllegalArgumentException("lifecycle listener name must not be blank");
        }
    }
}
