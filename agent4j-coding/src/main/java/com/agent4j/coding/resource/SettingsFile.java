package com.agent4j.coding.resource;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.Objects;

public record SettingsFile(
        ResourceScope scope,
        Path path,
        ObjectNode settings
) {
    public SettingsFile {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(settings, "settings");
        path = path.toAbsolutePath().normalize();
        settings = settings.deepCopy();
    }
}
