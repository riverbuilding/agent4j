package com.agent4j.coding.runtime;

import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.core.runtime.AgentLoopRequest;

import java.util.Objects;

public record PreparedAgentLoopRequest(
        AgentLoopRequest request,
        ResourceDiscovery discovery
) {
    public PreparedAgentLoopRequest {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(discovery, "discovery");
    }
}
