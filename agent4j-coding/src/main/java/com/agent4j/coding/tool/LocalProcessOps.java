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
import java.util.concurrent.TimeUnit;

public final class LocalProcessOps implements ProcessOps {
    @Override
    public ProcessResult run(List<String> command, Path cwd, Duration timeout) throws IOException, InterruptedException {
        Instant started = Instant.now();
        Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(false)
                .start();
        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor();
        }
        Duration duration = Duration.between(started, Instant.now());
        String stdout = readRemaining(process.getInputStream());
        String stderr = readRemaining(process.getErrorStream());
        return new ProcessResult(completed ? process.exitValue() : -1, stdout, stderr, duration, !completed);
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
