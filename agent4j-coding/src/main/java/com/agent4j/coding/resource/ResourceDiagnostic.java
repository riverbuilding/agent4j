package com.agent4j.coding.resource;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record ResourceDiagnostic(
        ResourceScope scope,
        Path path,
        String message
) {
    public ResourceDiagnostic {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
        path = path.toAbsolutePath().normalize();
    }

    public Optional<Path> optionalPath() {
        return Optional.of(path);
    }
}
