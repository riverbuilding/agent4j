package com.agent4j.coding.extension;

import java.util.Objects;

public record ExtensionProviderHookContribution(String extensionName, String name, ExtensionProviderHook hook) {
    public ExtensionProviderHookContribution {
        Objects.requireNonNull(extensionName, "extensionName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(hook, "hook");
        if (name.isBlank()) throw new IllegalArgumentException("provider hook name must not be blank");
    }
}
