package com.agent4j.coding.runtime;

import com.agent4j.coding.resource.AgentSettings;
import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.resource.SystemPromptBuilder;
import com.agent4j.core.runtime.AgentLoopRequest;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Enriches agent-loop requests with discovered resources, system prompts, and settings defaults. */
public final class CodingAgentLoopRequestPreparer {
    private final ResourceLoader resourceLoader;
    private final SystemPromptBuilder systemPromptBuilder;

    public CodingAgentLoopRequestPreparer() {
        this(new ResourceLoader(), new SystemPromptBuilder());
    }

    public CodingAgentLoopRequestPreparer(ResourceLoader resourceLoader, SystemPromptBuilder systemPromptBuilder) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
        this.systemPromptBuilder = Objects.requireNonNull(systemPromptBuilder, "systemPromptBuilder");
    }

    public PreparedAgentLoopRequest prepare(AgentLoopRequest request, Path homeDir) throws IOException {
        return prepare(request, ResourceDiscoveryOptions.enabled(homeDir, request.cwd()));
    }

    public PreparedAgentLoopRequest prepare(AgentLoopRequest request, ResourceDiscoveryOptions options) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(options, "options");
        ResourceDiscovery discovery = resourceLoader.discover(options);
        String systemPrompt = systemPromptBuilder.build(discovery);
        return new PreparedAgentLoopRequest(withSystemPromptAndSettings(request, systemPrompt, discovery.settings()), discovery);
    }

    private static AgentLoopRequest withSystemPromptAndSettings(
            AgentLoopRequest request,
            String systemPrompt,
            AgentSettings settings
    ) {
        return new AgentLoopRequest(
                request.sessionId(),
                request.turnId(),
                request.parentMessageId(),
                request.messages(),
                request.cwd(),
                request.clock(),
                request.abortSignal(),
                request.options().toBuilder()
                        .systemPrompt(systemPrompt)
                        .maxModelRetries(maxModelRetries(request, settings))
                        .modelTimeout(modelTimeout(request, settings))
                        .build(),
                request.liveQueues());
    }

    private static int maxModelRetries(AgentLoopRequest request, AgentSettings settings) {
        if (request.maxModelRetries() > 0) {
            return request.maxModelRetries();
        }
        JsonNode value = settings.values().at("/retry/maxRetries");
        return value.canConvertToInt() && value.asInt() >= 0 ? value.asInt() : request.maxModelRetries();
    }

    private static Optional<Duration> modelTimeout(AgentLoopRequest request, AgentSettings settings) {
        if (request.modelTimeout().isPresent()) {
            return request.modelTimeout();
        }
        return settings.intField("httpIdleTimeoutMs")
                .filter(value -> value > 0)
                .map(Duration::ofMillis);
    }
}
