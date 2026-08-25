package com.agent4j.coding.extension;

import java.util.Objects;

/**
 * Java-supplied extension that contributes capabilities during trusted runtime configuration.
 */
public interface AgentExtension {
    /** A stable, unique extension identifier. */
    String name();

    /**
     * Declares the extension's activation scope. Project-scoped extensions are not activated
     * while the project is untrusted.
     */
    default ExtensionScope scope() {
        return ExtensionScope.APPLICATION;
    }

    /**
     * Reserves an explicit trust requirement for future extension capabilities.
     *
     * <p>In this release project scope is the enforced trust boundary. Application-scoped
     * extensions remain application-controlled even when this method returns {@code true}.</p>
     */
    default boolean requiresProjectTrust() {
        return false;
    }

    /** Registers this extension's contributions in the supplied registry. */
    default void register(ExtensionContext context, ExtensionContributionRegistrar registrar) throws Exception {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(registrar, "registrar");
    }
}
