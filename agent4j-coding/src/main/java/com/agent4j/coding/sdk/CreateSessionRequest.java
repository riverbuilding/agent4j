package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModelReference;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record CreateSessionRequest(
        Path sessionFile,
        Path cwd,
        Optional<String> name,
        Optional<AiModelReference> model,
        Optional<String> sessionId
) {
    public CreateSessionRequest(Path sessionFile, Path cwd) {
        this(sessionFile, cwd, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public CreateSessionRequest(Path sessionFile, Path cwd, Optional<String> name, Optional<AiModelReference> model) {
        this(sessionFile, cwd, name, model, Optional.empty());
    }

    public CreateSessionRequest {
        Objects.requireNonNull(sessionFile, "sessionFile");
        Objects.requireNonNull(cwd, "cwd");
        name = name == null ? Optional.empty() : name;
        model = model == null ? Optional.empty() : model;
        sessionId = sessionId == null ? Optional.empty() : sessionId;
        name.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        });
        sessionId.ifPresent(value -> {
            if (!value.matches("[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?")) {
                throw new IllegalArgumentException("session ID contains invalid characters");
            }
        });
        sessionFile = sessionFile.toAbsolutePath().normalize();
        cwd = cwd.toAbsolutePath().normalize();
    }
}
