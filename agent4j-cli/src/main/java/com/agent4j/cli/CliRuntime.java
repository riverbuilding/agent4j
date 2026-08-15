package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderRegistry;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.sdk.CodingAgentRuntime;

import java.util.Objects;

public record CliRuntime(
        CodingAgentRuntime runtime,
        ResourceDiscovery resourceDiscovery,
        AiModelReference defaultModel,
        java.util.Optional<AiProviderRegistry> providerRegistry
) {
    public CliRuntime {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(resourceDiscovery, "resourceDiscovery");
        Objects.requireNonNull(defaultModel, "defaultModel");
        providerRegistry = providerRegistry == null ? java.util.Optional.empty() : providerRegistry;
    }

    public CliRuntime(CodingAgentRuntime runtime, ResourceDiscovery resourceDiscovery, AiModelReference defaultModel) {
        this(runtime, resourceDiscovery, defaultModel, java.util.Optional.empty());
    }
}
