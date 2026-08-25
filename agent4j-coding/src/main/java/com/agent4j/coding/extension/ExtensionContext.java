package com.agent4j.coding.extension;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Immutable runtime state available while trusted Java extensions are configured. */
public record ExtensionContext(Path workspace, Path sessionFile, boolean projectTrusted) {
    public ExtensionContext {
        Objects.requireNonNull(workspace, "workspace");
    }

    public Optional<Path> optionalSessionFile() {
        return Optional.ofNullable(sessionFile);
    }
}
