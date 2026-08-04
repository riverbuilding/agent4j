package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModelReference;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record CreateSessionRequest(
        Path sessionFile,
        Path cwd,
        Optional<String> name,
        Optional<AiModelReference> model
) {
    public CreateSessionRequest(Path sessionFile, Path cwd) {
        this(sessionFile, cwd, Optional.empty(), Optional.empty());
    }

    public CreateSessionRequest {
        Objects.requireNonNull(sessionFile, "sessionFile");
        Objects.requireNonNull(cwd, "cwd");
        name = name == null ? Optional.empty() : name;
        model = model == null ? Optional.empty() : model;
        name.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        });
        sessionFile = sessionFile.toAbsolutePath().normalize();
        cwd = cwd.toAbsolutePath().normalize();
    }
}
