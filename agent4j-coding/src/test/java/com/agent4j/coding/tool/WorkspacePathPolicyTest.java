package com.agent4j.coding.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspacePathPolicyTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsPathsThatTraverseSymbolicLinks() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.createSymbolicLink(workspace.resolve("linked"), outside);

        assertThatThrownBy(() -> new WorkspacePathPolicy().resolve(workspace, Path.of("linked/secret.txt")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbolic link");
    }
}
