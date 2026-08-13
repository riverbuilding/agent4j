package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderRegistry;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.sdk.AgentSessionRuntime;

import java.util.Objects;

public record CliRuntime(
        AgentSessionRuntime sessionRuntime,
        ResourceDiscovery resourceDiscovery,
        AiModelReference defaultModel,
        java.util.Optional<AiProviderRegistry> providerRegistry
) {
    public CliRuntime {
        Objects.requireNonNull(sessionRuntime, "sessionRuntime");
        Objects.requireNonNull(resourceDiscovery, "resourceDiscovery");
        Objects.requireNonNull(defaultModel, "defaultModel");
        providerRegistry = providerRegistry == null ? java.util.Optional.empty() : providerRegistry;
    }

    public CliRuntime(AgentSessionRuntime sessionRuntime, ResourceDiscovery resourceDiscovery, AiModelReference defaultModel) {
        this(sessionRuntime, resourceDiscovery, defaultModel, java.util.Optional.empty());
    }
}
