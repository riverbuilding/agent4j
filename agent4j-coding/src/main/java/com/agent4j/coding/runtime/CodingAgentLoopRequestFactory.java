package com.agent4j.coding.runtime;

import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.resource.SystemPromptBuilder;
import com.agent4j.core.runtime.AgentLoopRequest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * @deprecated since 0.1.0; use {@link CodingAgentLoopRequestPreparer}. This compatibility facade
 * delegates to the preparer and will be removed in the next breaking API release.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public final class CodingAgentLoopRequestFactory {
    private final CodingAgentLoopRequestPreparer preparer;

    public CodingAgentLoopRequestFactory() {
        this(new CodingAgentLoopRequestPreparer());
    }

    public CodingAgentLoopRequestFactory(ResourceLoader resourceLoader, SystemPromptBuilder systemPromptBuilder) {
        this(new CodingAgentLoopRequestPreparer(resourceLoader, systemPromptBuilder));
    }

    private CodingAgentLoopRequestFactory(CodingAgentLoopRequestPreparer preparer) {
        this.preparer = Objects.requireNonNull(preparer, "preparer");
    }

    public PreparedAgentLoopRequest prepare(AgentLoopRequest request, Path homeDir) throws IOException {
        return preparer.prepare(request, homeDir);
    }

    public PreparedAgentLoopRequest prepare(AgentLoopRequest request, ResourceDiscoveryOptions options) throws IOException {
        return preparer.prepare(request, options);
    }
}
