package com.agent4j.coding.extension;

/** Indicates that an explicitly configured extension provider could not be loaded. */
public final class ExtensionLoadException extends RuntimeException {
    public ExtensionLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
