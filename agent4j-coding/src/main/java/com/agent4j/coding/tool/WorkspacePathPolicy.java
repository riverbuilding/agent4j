package com.agent4j.coding.tool;

import com.agent4j.core.operation.PathPolicy;

import java.nio.file.Path;
import java.util.Objects;

public final class WorkspacePathPolicy implements PathPolicy {
    @Override
    public Path resolve(Path cwd, Path requestedPath) {
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(requestedPath, "requestedPath");
        Path root = cwd.toAbsolutePath().normalize();
        Path resolved = requestedPath.isAbsolute()
                ? requestedPath.toAbsolutePath().normalize()
                : root.resolve(requestedPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("path escapes workspace: " + requestedPath);
        }
        return resolved;
    }
}
