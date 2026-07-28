package com.agent4j.core.operation;

import java.time.Duration;

public record ProcessResult(int exitCode, String stdout, String stderr, Duration duration, boolean timedOut) {
    public ProcessResult {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }
}
