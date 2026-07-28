package com.agent4j.coding.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileSystemOpsTest {
    @TempDir
    Path tempDir;

    private final LocalFileSystemOps ops = new LocalFileSystemOps();

    @Test
    void readsAndWritesUtf8TextAndBytes() throws Exception {
        Path file = tempDir.resolve("nested/file.txt");

        ops.writeString(file, "hello");

        assertThat(ops.exists(file)).isTrue();
        assertThat(ops.readString(file)).isEqualTo("hello");
        assertThat(ops.readBytes(file)).containsExactly("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void listsAndWalksEntriesInStablePathOrder() throws Exception {
        Files.createDirectories(tempDir.resolve("src/b"));
        Files.createDirectories(tempDir.resolve("src/a"));
        Files.writeString(tempDir.resolve("src/Main.java"), "class Main {}");

        assertThat(ops.list(tempDir.resolve("src"))).extracting(entry -> entry.path().getFileName().toString())
                .containsExactly("Main.java", "a", "b");
        assertThat(ops.walk(tempDir.resolve("src"))).extracting(entry -> tempDir.resolve("src").relativize(entry.path()).toString())
                .containsExactly("Main.java", "a", "b");
    }
}
