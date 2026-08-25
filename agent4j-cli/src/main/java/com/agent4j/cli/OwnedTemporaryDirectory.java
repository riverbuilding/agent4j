package com.agent4j.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

/** Owns and best-effort cleans only a directory created by this type. */
final class OwnedTemporaryDirectory implements AutoCloseable {
    private final Path path;

    private OwnedTemporaryDirectory(Path path) {
        this.path = path;
    }

    static OwnedTemporaryDirectory create(String prefix) throws IOException {
        return new OwnedTemporaryDirectory(Files.createTempDirectory(prefix));
    }

    static OwnedTemporaryDirectory create(Path parent, String prefix) throws IOException {
        return new OwnedTemporaryDirectory(Files.createTempDirectory(Objects.requireNonNull(parent, "parent"), prefix));
    }

    Path path() {
        return path;
    }

    @Override
    public void close() {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException ignored) {
                    // Temporary-directory cleanup must not mask command output.
                }
            });
        } catch (IOException ignored) {
            // Temporary-directory cleanup must not mask command output.
        }
    }
}
