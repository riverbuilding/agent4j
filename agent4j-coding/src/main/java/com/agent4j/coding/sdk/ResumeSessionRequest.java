package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModelReference;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record ResumeSessionRequest(
        Path sessionFile,
        Optional<String> activeEntryId,
        Optional<AiModelReference> model
) {
    public ResumeSessionRequest(Path sessionFile) {
        this(sessionFile, Optional.empty(), Optional.empty());
    }

    public ResumeSessionRequest {
        Objects.requireNonNull(sessionFile, "sessionFile");
        activeEntryId = activeEntryId == null ? Optional.empty() : activeEntryId;
        model = model == null ? Optional.empty() : model;
        activeEntryId.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("activeEntryId must not be blank");
            }
        });
        sessionFile = sessionFile.toAbsolutePath().normalize();
    }
}
