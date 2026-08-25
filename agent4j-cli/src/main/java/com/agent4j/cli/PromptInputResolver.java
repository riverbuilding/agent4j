package com.agent4j.cli;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Resolves text prompt arguments, including stdin and @file inclusions. */
final class PromptInputResolver {
    private PromptInputResolver() {
    }

    static List<String> resolve(List<String> messages, Reader input, boolean readStdin, Path cwd) throws IOException {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(cwd, "cwd");
        List<String> resolved = new ArrayList<>();
        for (String message : messages) {
            resolved.add(resolveFile(message, cwd));
        }
        if (readStdin) {
            String stdin = readAll(input).strip();
            if (!stdin.isEmpty()) {
                resolved.add(stdin);
            }
        }
        return List.copyOf(resolved);
    }

    private static String resolveFile(String message, Path cwd) throws IOException {
        if (message == null || !message.startsWith("@") || message.startsWith("@@")) {
            return message == null ? "" : message;
        }
        String fileName = message.substring(1);
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("@file requires a path");
        }
        Path file = cwd.resolve(fileName).normalize();
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("prompt file does not exist or is not a regular file: " + file);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static String readAll(Reader input) throws IOException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            text.append(buffer, 0, count);
        }
        return text.toString();
    }
}
