package com.agent4j.cli;

import java.nio.file.Path;
import java.util.Objects;

public record CliEnvironment(Path cwd, Path homeDirectory) {
    public CliEnvironment {
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(homeDirectory, "homeDirectory");
        cwd = cwd.toAbsolutePath().normalize();
        homeDirectory = homeDirectory.toAbsolutePath().normalize();
    }

    public static CliEnvironment system() {
        return new CliEnvironment(Path.of("."), Path.of(System.getProperty("user.home")));
    }
}
