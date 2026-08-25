package com.agent4j.coding.extension;

import java.util.Map;

/** Allowed non-authentication request changes from an extension provider hook. */
public record ExtensionProviderRequestMutation(Map<String, String> headers, Map<String, Object> attributes) {
    public ExtensionProviderRequestMutation {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
    public static ExtensionProviderRequestMutation none() { return new ExtensionProviderRequestMutation(Map.of(), Map.of()); }
}
