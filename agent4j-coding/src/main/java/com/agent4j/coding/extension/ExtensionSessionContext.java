package com.agent4j.coding.extension;

import java.util.Objects;

/** Narrow immutable context supplied to session lifecycle listeners. */
public record ExtensionSessionContext(ExtensionSessionMetadata session, boolean projectTrusted) {
    public ExtensionSessionContext {
        Objects.requireNonNull(session, "session");
    }
}
