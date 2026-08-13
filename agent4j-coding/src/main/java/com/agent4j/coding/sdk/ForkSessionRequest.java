package com.agent4j.coding.sdk;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record ForkSessionRequest(
        AgentSession source,
        Path targetFile,
        Optional<String> activeEntryId,
        Optional<Path> cwd
) {
    public ForkSessionRequest(AgentSession source, Path targetFile) {
        this(source, targetFile, Optional.empty(), Optional.empty());
    }

    public ForkSessionRequest(AgentSession source, Path targetFile, Optional<String> activeEntryId) {
        this(source, targetFile, activeEntryId, Optional.empty());
    }

    public ForkSessionRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetFile, "targetFile");
        activeEntryId = activeEntryId == null ? Optional.empty() : activeEntryId;
        cwd = cwd == null ? Optional.empty() : cwd.map(Path::toAbsolutePath).map(Path::normalize);
        activeEntryId.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("activeEntryId must not be blank");
            }
        });
        targetFile = targetFile.toAbsolutePath().normalize();
    }
}
