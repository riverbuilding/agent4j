package com.agent4j.coding.sdk;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record ForkSessionRequest(
        AgentSession source,
        Path targetFile,
        Optional<String> activeEntryId
) {
    public ForkSessionRequest(AgentSession source, Path targetFile) {
        this(source, targetFile, Optional.empty());
    }

    public ForkSessionRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetFile, "targetFile");
        activeEntryId = activeEntryId == null ? Optional.empty() : activeEntryId;
        activeEntryId.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("activeEntryId must not be blank");
            }
        });
        targetFile = targetFile.toAbsolutePath().normalize();
    }
}
