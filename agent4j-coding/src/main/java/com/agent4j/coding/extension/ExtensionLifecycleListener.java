package com.agent4j.coding.extension;

import java.util.Objects;

/** Receives session lifecycle notifications after the extension runtime is integrated. */
public interface ExtensionLifecycleListener {
    default void beforeSessionOperation(ExtensionSessionOperation operation, ExtensionSessionContext context) throws Exception {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(context, "context");
    }

    default void afterSessionOperation(ExtensionSessionOperation operation, ExtensionSessionContext context) throws Exception {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(context, "context");
    }

    default void onSessionStart(ExtensionContext context) throws Exception {
        Objects.requireNonNull(context, "context");
    }

    default void onSessionShutdown(ExtensionContext context) throws Exception {
        Objects.requireNonNull(context, "context");
    }
}
