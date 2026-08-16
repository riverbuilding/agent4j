package com.agent4j.coding.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CodingAgentConfigRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void cleanupDeletesOnlyPathsExplicitlyOwnedByTheRuntime() throws Exception {
        Path workspace = temporaryDirectory.resolve("workspace");
        Path sessions = temporaryDirectory.resolve("sessions");
        CodingAgentConfig config = CodingAgentConfig.builder("test-key", "gpt-5", workspace, sessions)
                .ownsWorkspace(true)
                .ownsSessionDirectory(false)
                .build();

        CodingAgentRuntime runtime = CodingAgentRuntime.create(config);
        assertThat(runtime.workspace()).isEqualTo(workspace);
        assertThat(runtime.sessionDirectory()).isEqualTo(sessions);
        assertThat(workspace).exists();
        assertThat(sessions).exists();

        runtime.cleanupOwnedFiles();

        assertThat(workspace).doesNotExist();
        assertThat(sessions).exists();
    }
}
