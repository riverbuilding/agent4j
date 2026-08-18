package com.agent4j.cli;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record CliRuntimeRequest(
        Path cwd,
        Path homeDirectory,
        Optional<String> provider,
        Optional<String> model,
        Optional<String> apiKey,
        Optional<String> baseUrl,
        CliToolSelection toolSelection,
        Optional<String> systemPrompt,
        java.util.List<String> appendSystemPrompts
) {
    public CliRuntimeRequest(Path cwd, Path homeDirectory, Optional<String> provider, Optional<String> model, Optional<String> apiKey) {
        this(cwd, homeDirectory, provider, model, apiKey, Optional.empty(), CliToolSelection.defaults(), Optional.empty(), java.util.List.of());
    }

    public CliRuntimeRequest(
            Path cwd,
            Path homeDirectory,
            Optional<String> provider,
            Optional<String> model,
            Optional<String> apiKey,
            Optional<String> baseUrl,
            CliToolSelection toolSelection
    ) {
        this(cwd, homeDirectory, provider, model, apiKey, baseUrl, toolSelection, Optional.empty(), java.util.List.of());
    }

    public CliRuntimeRequest {
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(homeDirectory, "homeDirectory");
        provider = normalize(provider, "provider");
        model = normalize(model, "model");
        apiKey = normalize(apiKey, "apiKey");
        baseUrl = normalize(baseUrl, "baseUrl");
        toolSelection = toolSelection == null ? CliToolSelection.defaults() : toolSelection;
        systemPrompt = normalize(systemPrompt, "systemPrompt");
        appendSystemPrompts = appendSystemPrompts == null ? java.util.List.of() : appendSystemPrompts.stream()
                .map(value -> Objects.requireNonNull(value, "appendSystemPrompts must not contain null").strip())
                .filter(value -> !value.isEmpty())
                .toList();
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
