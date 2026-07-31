package com.agent4j.coding.runtime;

import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.resource.SystemPromptBuilder;
import com.agent4j.core.runtime.AgentLoopRequest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class CodingAgentLoopRequestFactory {
    private final ResourceLoader resourceLoader;
    private final SystemPromptBuilder systemPromptBuilder;

    public CodingAgentLoopRequestFactory() {
        this(new ResourceLoader(), new SystemPromptBuilder());
    }

    public CodingAgentLoopRequestFactory(ResourceLoader resourceLoader, SystemPromptBuilder systemPromptBuilder) {
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
        return new PreparedAgentLoopRequest(withSystemPrompt(request, systemPrompt), discovery);
    }

    private static AgentLoopRequest withSystemPrompt(AgentLoopRequest request, String systemPrompt) {
        return new AgentLoopRequest(
                request.sessionId(),
                request.turnId(),
                request.parentMessageId(),
                request.messages(),
                request.cwd(),
                request.clock(),
                request.abortSignal(),
                request.toolAttributes(),
                systemPrompt,
                request.maxToolRounds(),
                request.maxModelRetries(),
                request.toolExecutionMode(),
                request.promptMessages(),
                request.steeringMessages(),
                request.followUpMessages(),
                request.steeringMode(),
                request.followUpMode());
    }
}
