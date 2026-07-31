package com.agent4j.coding.resource;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record PromptTemplate(
        String name,
        ResourceScope scope,
        Path path,
        String content,
        Optional<String> description,
        Optional<String> argumentHint
) {
    public PromptTemplate {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(argumentHint, "argumentHint");
        path = path.toAbsolutePath().normalize();
    }
}
