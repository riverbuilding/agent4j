package com.agent4j.core.operation;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface ProcessOps {
    ProcessResult run(List<String> command, Path cwd, Duration timeout) throws IOException, InterruptedException;
}
