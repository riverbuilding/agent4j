package com.agent4j.coding.extension;

import com.agent4j.ai.AiAuthMode;
import com.agent4j.ai.AiModelReference;

import java.util.Map;

/** Credential-free view of a provider request. */
public record ExtensionProviderRequest(
        AiModelReference model,
        AiAuthMode authMode,
        Map<String, String> headers,
        Map<String, Object> attributes,
        int messageCount,
        int toolCount
) {
    public ExtensionProviderRequest {
        headers = Map.copyOf(headers);
        attributes = Map.copyOf(attributes);
    }
}
