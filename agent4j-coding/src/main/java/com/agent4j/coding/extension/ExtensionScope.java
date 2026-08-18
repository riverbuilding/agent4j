package com.agent4j.coding.extension;

/**
 * Declares whether an extension is supplied by the embedding application or is scoped to a
 * particular project.
 */
public enum ExtensionScope {
    /** An extension supplied by application code or the application classpath. */
    APPLICATION,

    /** An extension whose activation is associated with the current project. */
    PROJECT
}
