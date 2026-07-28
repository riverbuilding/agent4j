package com.agent4j.core.operation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface FileSystemOps {
    byte[] readBytes(Path path) throws IOException;

    String readString(Path path) throws IOException;

    void writeString(Path path, String content) throws IOException;

    boolean exists(Path path);

    boolean isDirectory(Path path);

    List<FileSystemEntry> list(Path path) throws IOException;

    List<FileSystemEntry> walk(Path path) throws IOException;
}
