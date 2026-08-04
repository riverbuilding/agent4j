package com.agent4j.coding.sdk;

import java.nio.file.Path;
import java.util.Objects;

public record ImportSessionRequest(
        Path sourceFile,
        Path targetFile
) {
    public ImportSessionRequest {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(targetFile, "targetFile");
        sourceFile = sourceFile.toAbsolutePath().normalize();
        targetFile = targetFile.toAbsolutePath().normalize();
    }
}
