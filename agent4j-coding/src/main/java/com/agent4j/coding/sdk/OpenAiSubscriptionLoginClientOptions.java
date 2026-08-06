package com.agent4j.coding.sdk;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record OpenAiSubscriptionLoginClientOptions(
        String clientId,
        URI authorizationEndpoint,
        URI tokenEndpoint,
        Optional<URI> deviceAuthorizationEndpoint,
        Optional<URI> redirectUri,
        List<String> scopes,
        Optional<String> baseUrl,
        boolean useHostedLoginSuccessPage,
        String appBrand,
        Duration defaultBrowserFlowTtl,
        Map<String, String> headers
) {
    public OpenAiSubscriptionLoginClientOptions {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(authorizationEndpoint, "authorizationEndpoint");
        Objects.requireNonNull(tokenEndpoint, "tokenEndpoint");
        deviceAuthorizationEndpoint = deviceAuthorizationEndpoint == null ? Optional.empty() : deviceAuthorizationEndpoint;
        redirectUri = redirectUri == null ? Optional.empty() : redirectUri;
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        baseUrl = baseUrl == null ? Optional.empty() : baseUrl;
        appBrand = appBrand == null || appBrand.isBlank() ? "chatgpt" : appBrand;
        defaultBrowserFlowTtl = defaultBrowserFlowTtl == null ? Duration.ofMinutes(10) : defaultBrowserFlowTtl;
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        if (clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        if (defaultBrowserFlowTtl.isZero() || defaultBrowserFlowTtl.isNegative()) {
            throw new IllegalArgumentException("defaultBrowserFlowTtl must be positive");
        }
    }

    public static Builder builder(String clientId, URI authorizationEndpoint, URI tokenEndpoint) {
        return new Builder(clientId, authorizationEndpoint, tokenEndpoint);
    }

    public String scope() {
        return String.join(" ", scopes);
    }

    public static final class Builder {
        private final String clientId;
        private final URI authorizationEndpoint;
        private final URI tokenEndpoint;
        private URI deviceAuthorizationEndpoint;
        private URI redirectUri;
        private List<String> scopes = List.of("openid", "profile", "email", "offline_access");
        private String baseUrl;
        private boolean useHostedLoginSuccessPage = true;
        private String appBrand = "chatgpt";
        private Duration defaultBrowserFlowTtl = Duration.ofMinutes(10);
        private Map<String, String> headers = Map.of();

        private Builder(String clientId, URI authorizationEndpoint, URI tokenEndpoint) {
            this.clientId = Objects.requireNonNull(clientId, "clientId");
            this.authorizationEndpoint = Objects.requireNonNull(authorizationEndpoint, "authorizationEndpoint");
            this.tokenEndpoint = Objects.requireNonNull(tokenEndpoint, "tokenEndpoint");
        }

        public Builder deviceAuthorizationEndpoint(URI deviceAuthorizationEndpoint) {
            this.deviceAuthorizationEndpoint = Objects.requireNonNull(deviceAuthorizationEndpoint, "deviceAuthorizationEndpoint");
            return this;
        }

        public Builder redirectUri(URI redirectUri) {
            this.redirectUri = Objects.requireNonNull(redirectUri, "redirectUri");
            return this;
        }

        public Builder scopes(List<String> scopes) {
            this.scopes = List.copyOf(Objects.requireNonNull(scopes, "scopes"));
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            return this;
        }

        public Builder useHostedLoginSuccessPage(boolean useHostedLoginSuccessPage) {
            this.useHostedLoginSuccessPage = useHostedLoginSuccessPage;
            return this;
        }

        public Builder appBrand(String appBrand) {
            this.appBrand = Objects.requireNonNull(appBrand, "appBrand");
            return this;
        }

        public Builder defaultBrowserFlowTtl(Duration defaultBrowserFlowTtl) {
            this.defaultBrowserFlowTtl = Objects.requireNonNull(defaultBrowserFlowTtl, "defaultBrowserFlowTtl");
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
            return this;
        }

        public OpenAiSubscriptionLoginClientOptions build() {
            return new OpenAiSubscriptionLoginClientOptions(
                    clientId,
                    authorizationEndpoint,
                    tokenEndpoint,
                    Optional.ofNullable(deviceAuthorizationEndpoint),
                    Optional.ofNullable(redirectUri),
                    scopes,
                    Optional.ofNullable(baseUrl),
                    useHostedLoginSuccessPage,
                    appBrand,
                    defaultBrowserFlowTtl,
                    headers);
        }
    }
}
