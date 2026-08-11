package com.agent4j.cli;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record CliRuntimeRequest(
        Path cwd,
        Path homeDirectory,
        Optional<String> provider,
        Optional<String> model,
        Optional<String> apiKey
) {
    public CliRuntimeRequest {
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(homeDirectory, "homeDirectory");
        provider = normalize(provider, "provider");
        model = normalize(model, "model");
        apiKey = normalize(apiKey, "apiKey");
        cwd = cwd.toAbsolutePath().normalize();
        homeDirectory = homeDirectory.toAbsolutePath().normalize();
    }

    private static Optional<String> normalize(Optional<String> value, String name) {
        Optional<String> optional = value == null ? Optional.empty() : value;
        if (optional.isPresent() && optional.orElseThrow().isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return optional.map(String::strip);
    }
}
