package com.agent4j.coding.sdk;

import java.nio.file.Path;
import java.util.Objects;

public record CloneSessionRequest(
        AgentSession source,
        Path targetFile
) {
    public CloneSessionRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetFile, "targetFile");
        targetFile = targetFile.toAbsolutePath().normalize();
    }
}
