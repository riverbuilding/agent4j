package com.agent4j.cli;

import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderRegistry;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.SystemPromptBuilder;
import com.agent4j.coding.sdk.CodingAgentRuntime;

import java.util.Objects;
import java.util.Optional;

public record CliRuntime(
        CodingAgentRuntime runtime,
        ResourceDiscovery resourceDiscovery,
        AiModelReference defaultModel,
        java.util.Optional<AiProviderRegistry> providerRegistry,
        String systemPrompt
) {
    public CliRuntime {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(resourceDiscovery, "resourceDiscovery");
        Objects.requireNonNull(defaultModel, "defaultModel");
        providerRegistry = providerRegistry == null ? java.util.Optional.empty() : providerRegistry;
        systemPrompt = Objects.requireNonNull(systemPrompt, "systemPrompt");
    }

    public CliRuntime(CodingAgentRuntime runtime, ResourceDiscovery resourceDiscovery, AiModelReference defaultModel) {
        this(runtime, resourceDiscovery, defaultModel, java.util.Optional.empty(), new SystemPromptBuilder().build(resourceDiscovery));
    }

    public CliRuntime(
            CodingAgentRuntime runtime,
            ResourceDiscovery resourceDiscovery,
            AiModelReference defaultModel,
            Optional<AiProviderRegistry> providerRegistry
    ) {
        this(runtime, resourceDiscovery, defaultModel, providerRegistry, new SystemPromptBuilder().build(resourceDiscovery));
    }

    public java.util.Optional<AiModelReference> promptModel() {
        return providerRegistry.map(ignored -> defaultModel);
    }
}
