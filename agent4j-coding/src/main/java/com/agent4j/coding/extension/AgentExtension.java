package com.agent4j.coding.extension;

import java.util.Objects;

/**
 * Java-supplied extension that contributes capabilities during trusted runtime configuration.
 */
public interface AgentExtension {
    /** A stable, unique extension identifier. */
    String name();

    /** Registers this extension's contributions in the supplied registry. */
    default void register(ExtensionContext context, ExtensionContributionRegistrar registrar) throws Exception {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(registrar, "registrar");
    }
}
