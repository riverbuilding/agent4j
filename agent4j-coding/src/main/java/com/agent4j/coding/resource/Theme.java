package com.agent4j.coding.resource;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.Objects;

public record Theme(
        String name,
        ResourceScope scope,
        Path path,
        ObjectNode definition
) {
    public Theme {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(definition, "definition");
        path = path.toAbsolutePath().normalize();
        definition = definition.deepCopy();
    }
}
