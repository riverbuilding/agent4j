package com.agent4j.coding.tool;

import com.agent4j.core.operation.ProcessOps;
import com.agent4j.core.operation.ProcessResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class LocalProcessOps implements ProcessOps {
    @Override
    public ProcessResult run(List<String> command, Path cwd, Duration timeout) throws IOException, InterruptedException {
        Instant started = Instant.now();
        Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(false)
                .start();
        try (ExecutorService readers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> stdout = readers.submit(() -> readRemaining(process.getInputStream()));
            Future<String> stderr = readers.submit(() -> readRemaining(process.getErrorStream()));
            boolean completed;
            try {
                completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    process.waitFor();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                process.waitFor();
                throw e;
            }
            Duration duration = Duration.between(started, Instant.now());
            return new ProcessResult(completed ? process.exitValue() : -1, await(stdout), await(stderr), duration, !completed);
        }
    }

    private static String await(Future<String> reader) throws IOException, InterruptedException {
        try {
            return reader.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("unable to read process output", e.getCause());
        }
    }

    private static String readRemaining(InputStream stream) throws IOException {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            if ("Stream closed".equals(e.getMessage())) {
                return "";
            }
            throw e;
        }
    }
}
