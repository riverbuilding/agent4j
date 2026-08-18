package com.agent4j.coding.extension;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Runtime state shared with coding-extension lifecycle methods. */
public record CodingExtensionContext(Path workspace, Path sessionFile, boolean projectTrusted) {
    public CodingExtensionContext {
        Objects.requireNonNull(workspace, "workspace");
    }

    public Optional<Path> optionalSessionFile() {
        return Optional.ofNullable(sessionFile);
    }
}
