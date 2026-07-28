package com.agent4j.coding.tool;

import com.agent4j.core.operation.FileSystemOps;
import com.agent4j.core.operation.FileSystemEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class LocalFileSystemOps implements FileSystemOps {
    @Override
    public byte[] readBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public String readString(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Override
    public void writeString(Path path, String content) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    @Override
    public boolean exists(Path path) {
        return Files.exists(path);
    }

    @Override
    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    @Override
    public List<FileSystemEntry> list(Path path) throws IOException {
        try (Stream<Path> stream = Files.list(path)) {
            return stream
                    .sorted(Comparator.comparing(Path::toString))
                    .map(entry -> new FileSystemEntry(entry, Files.isDirectory(entry)))
                    .toList();
        }
    }

    @Override
    public List<FileSystemEntry> walk(Path path) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            return stream
                    .filter(entry -> !entry.equals(path))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(entry -> new FileSystemEntry(entry, Files.isDirectory(entry)))
                    .toList();
        }
    }
}
