package com.agent4j.coding.resource;

import java.nio.file.Path;
import java.util.Objects;

public record AgentResourceDirectories(
        Path globalAgentDir,
        Path projectAgentDir
) {
    public AgentResourceDirectories {
        Objects.requireNonNull(globalAgentDir, "globalAgentDir");
        Objects.requireNonNull(projectAgentDir, "projectAgentDir");
        globalAgentDir = globalAgentDir.toAbsolutePath().normalize();
        projectAgentDir = projectAgentDir.toAbsolutePath().normalize();
    }
}
