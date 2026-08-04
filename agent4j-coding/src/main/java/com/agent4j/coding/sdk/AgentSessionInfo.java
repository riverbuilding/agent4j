package com.agent4j.coding.sdk;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record AgentSessionInfo(
        String id,
        Path sessionFile,
        Path cwd,
        String activeEntryId
) {
    public AgentSessionInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sessionFile, "sessionFile");
        Objects.requireNonNull(cwd, "cwd");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        sessionFile = sessionFile.toAbsolutePath().normalize();
        cwd = cwd.toAbsolutePath().normalize();
    }

    public Optional<String> optionalActiveEntryId() {
        return Optional.ofNullable(activeEntryId);
    }
}
