package com.agent4j.coding.sdk;

import com.agent4j.coding.resource.ResourceDiscovery;

import java.nio.file.Path;
import java.util.Objects;

/** Resource-derived prompt state for one session workspace. */
public record ResolvedPromptContext(Path workspace, ResourceDiscovery discovery, String systemPrompt) {
    public ResolvedPromptContext {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(discovery, "discovery");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        workspace = workspace.toAbsolutePath().normalize();
    }
}
