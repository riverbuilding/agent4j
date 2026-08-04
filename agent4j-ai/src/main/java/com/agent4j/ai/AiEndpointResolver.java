package com.agent4j.ai;

import java.net.URI;
import java.util.Objects;

public final class AiEndpointResolver {
    private AiEndpointResolver() {
    }

    public static AiModel applyAuthBaseUrl(AiModel model, AiResolvedAuth auth) {
        Objects.requireNonNull(model, "model");
        if (auth == null || auth.baseUrl().isEmpty()) {
            return model;
        }
        String baseUrl = auth.baseUrl().orElseThrow().strip();
        return baseUrl.isBlank() ? model : model.withBaseUrl(baseUrl);
    }

    public static URI endpoint(AiModel model, URI defaultEndpoint, String pathSuffix) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(defaultEndpoint, "defaultEndpoint");
        Objects.requireNonNull(pathSuffix, "pathSuffix");
        return model.baseUrl()
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .map(baseUrl -> normalize(baseUrl, pathSuffix))
                .map(URI::create)
                .orElse(defaultEndpoint);
    }

    private static String normalize(String baseUrl, String pathSuffix) {
        String suffix = pathSuffix.startsWith("/") ? pathSuffix : "/" + pathSuffix;
        return baseUrl.endsWith(suffix) ? baseUrl : stripTrailingSlash(baseUrl) + suffix;
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
