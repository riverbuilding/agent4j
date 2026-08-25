package com.agent4j.coding.session;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

final class SessionFileStore {
    private final Path sessionFile;
    private final SessionJsonlCodec codec;

    SessionFileStore(Path sessionFile, SessionJsonlCodec codec) {
        this.sessionFile = Objects.requireNonNull(sessionFile, "sessionFile");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    SessionDocument read() throws IOException {
        SessionDocument document;
        try (StringReader reader = new StringReader(Files.readString(sessionFile))) {
            document = codec.read(reader);
        }
        SessionDocumentValidator.validate(document);
        return document;
    }

    void create(SessionDocument document) throws IOException {
        if (Files.exists(sessionFile)) {
            throw new IOException("session file already exists: " + sessionFile);
        }
        write(document);
    }

    void appendIfFresh(SessionDocument snapshot, List<String> lines) throws IOException {
        Files.createDirectories(sessionFile.toAbsolutePath().getParent());
        try (FileChannel channel = FileChannel.open(
                sessionFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
             FileLock ignored = channel.lock()) {
            assertSnapshotIsFresh(snapshot);
            for (String line : lines) {
                byte[] bytes = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
                channel.write(ByteBuffer.wrap(bytes));
            }
        }
    }

    void write(SessionDocument document) throws IOException {
        Files.createDirectories(sessionFile.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(
                sessionFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            codec.write(document, writer);
        }
    }

    Path sessionFile() {
        return sessionFile;
    }

    private void assertSnapshotIsFresh(SessionDocument snapshot) throws IOException {
        SessionDocument diskDocument = read();
        if (!snapshot.header().payload().equals(diskDocument.header().payload())
                || snapshot.entries().size() != diskDocument.entries().size()) {
            throw staleSessionSnapshot();
        }
        for (int i = 0; i < snapshot.entries().size(); i++) {
            if (!snapshot.entries().get(i).payload().equals(diskDocument.entries().get(i).payload())) {
                throw staleSessionSnapshot();
            }
        }
    }

    private IllegalStateException staleSessionSnapshot() {
        return new IllegalStateException("session file changed on disk; reopen before appending: " + sessionFile);
    }
}
