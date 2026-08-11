package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.sdk.AgentSessionRuntime;

import java.util.Objects;

public record CliRuntime(
        AgentSessionRuntime sessionRuntime,
        ResourceDiscovery resourceDiscovery,
        AiModelReference defaultModel
) {
    public CliRuntime {
        Objects.requireNonNull(sessionRuntime, "sessionRuntime");
        Objects.requireNonNull(resourceDiscovery, "resourceDiscovery");
        Objects.requireNonNull(defaultModel, "defaultModel");
    }
}
