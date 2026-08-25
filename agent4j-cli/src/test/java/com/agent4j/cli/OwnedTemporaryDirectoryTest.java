package com.agent4j.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OwnedTemporaryDirectoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void cleansOnlyTheDirectoryItCreated() throws Exception {
        Path sentinel = temporaryDirectory.resolve("keep.txt");
        Files.writeString(sentinel, "preserve me");
        OwnedTemporaryDirectory owned = OwnedTemporaryDirectory.create(temporaryDirectory, "owned-");
        Files.writeString(owned.path().resolve("session.jsonl"), "temporary session");

        owned.close();

        assertThat(owned.path()).doesNotExist();
        assertThat(sentinel).hasContent("preserve me");
    }
}
