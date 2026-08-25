package com.agent4j.coding.extension;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Immutable, public session identity exposed to lifecycle listeners. */
public record ExtensionSessionMetadata(
        Optional<String> sessionId,
        Path sessionFile,
        Path workspace,
        Optional<String> activeEntryId
) {
    public ExtensionSessionMetadata {
        sessionId = sessionId == null ? Optional.empty() : sessionId;
        sessionFile = Objects.requireNonNull(sessionFile, "sessionFile").toAbsolutePath().normalize();
        workspace = Objects.requireNonNull(workspace, "workspace").toAbsolutePath().normalize();
        activeEntryId = activeEntryId == null ? Optional.empty() : activeEntryId;
    }
}
