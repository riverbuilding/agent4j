package com.agent4j.coding.resource;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Skill(
        String name,
        String description,
        ResourceScope scope,
        Path path,
        Path root,
        String content,
        Optional<String> license,
        Optional<String> compatibility,
        List<String> allowedTools,
        boolean disableModelInvocation
) {
    public Skill {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(license, "license");
        Objects.requireNonNull(compatibility, "compatibility");
        Objects.requireNonNull(allowedTools, "allowedTools");
        path = path.toAbsolutePath().normalize();
        root = root.toAbsolutePath().normalize();
        allowedTools = List.copyOf(allowedTools);
    }
}
