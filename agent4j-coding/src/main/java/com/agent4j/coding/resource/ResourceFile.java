package com.agent4j.coding.resource;

import java.nio.file.Path;
import java.util.Objects;

public record ResourceFile(
        ResourceScope scope,
        ResourceFileType type,
        Path path,
        String content
) {
    public ResourceFile {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(content, "content");
        path = path.toAbsolutePath().normalize();
    }
}
