package com.agent4j.core.operation;

import java.nio.file.Path;
import java.util.Objects;

public record FileSystemEntry(Path path, boolean directory) {
    public FileSystemEntry {
        Objects.requireNonNull(path, "path");
    }
}
