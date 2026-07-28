package com.agent4j.core.operation;

import java.nio.file.Path;

public interface PathPolicy {
    Path resolve(Path cwd, Path requestedPath);
}
