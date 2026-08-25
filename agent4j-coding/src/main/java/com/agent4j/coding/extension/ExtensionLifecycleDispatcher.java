package com.agent4j.coding.extension;

import java.util.List;
import java.util.Objects;

/** Dispatches lifecycle listeners with fail-fast pre-operation and diagnostic-only post-operation behavior. */
public final class ExtensionLifecycleDispatcher {
    private static final System.Logger LOGGER = System.getLogger(ExtensionLifecycleDispatcher.class.getName());

    private ExtensionLifecycleDispatcher() {
    }

    public static void before(
            List<ExtensionLifecycleListenerContribution> contributions,
            ExtensionSessionOperation operation,
            ExtensionSessionContext context
    ) throws Exception {
        for (ExtensionLifecycleListenerContribution contribution : List.copyOf(Objects.requireNonNull(contributions))) {
            contribution.listener().beforeSessionOperation(operation, context);
        }
    }

    public static void after(
            List<ExtensionLifecycleListenerContribution> contributions,
            ExtensionSessionOperation operation,
            ExtensionSessionContext context
    ) {
        for (ExtensionLifecycleListenerContribution contribution : List.copyOf(Objects.requireNonNull(contributions))) {
            try {
                contribution.listener().afterSessionOperation(operation, context);
            } catch (Exception error) {
                LOGGER.log(System.Logger.Level.WARNING, "extension {0} lifecycle listener {1} failed after {2}: {3}",
                        contribution.extensionName(), contribution.name(), operation, error.toString());
            }
        }
    }
}
