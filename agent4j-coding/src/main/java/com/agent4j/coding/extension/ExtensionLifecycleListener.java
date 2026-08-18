package com.agent4j.coding.extension;

import java.util.Objects;

/** Receives session lifecycle notifications after the extension runtime is integrated. */
public interface ExtensionLifecycleListener {
    default void onSessionStart(ExtensionContext context) throws Exception {
        Objects.requireNonNull(context, "context");
    }

    default void onSessionShutdown(ExtensionContext context) throws Exception {
        Objects.requireNonNull(context, "context");
    }
}
