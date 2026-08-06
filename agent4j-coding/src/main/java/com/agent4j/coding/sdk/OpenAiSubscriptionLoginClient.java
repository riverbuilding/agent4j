package com.agent4j.coding.sdk;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenAiSubscriptionLoginClient implements SubscriptionLoginClient {
    private static final String DEVICE_CODE_GRANT = "urn:ietf:params:oauth:grant-type:device_code";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OpenAiSubscriptionLoginClientOptions options;
    private final OpenAiSubscriptionLoginHttpTransport transport;
    private final Map<String, Flow> flows = new ConcurrentHashMap<>();
    private final Map<String, String> browserFlowIdsByState = new ConcurrentHashMap<>();

    public OpenAiSubscriptionLoginClient(OpenAiSubscriptionLoginClientOptions options) {
        this(options, new DefaultOpenAiSubscriptionLoginHttpTransport());
    }

    public OpenAiSubscriptionLoginClient(
            OpenAiSubscriptionLoginClientOptions options,
            OpenAiSubscriptionLoginHttpTransport transport
    ) {
        this.options = Objects.requireNonNull(options, "options");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    @Override
    public SubscriptionLoginStart startBrowserLogin(BrowserSubscriptionLoginRequest request, Instant now) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(now, "now");
        URI redirectUri = request.redirectUri().or(() -> options.redirectUri()).orElseThrow(() ->
                new IllegalStateException("redirectUri is required for browser subscription login"));
        String flowId = token(24);
        String verifier = token(48);
        String state = token(24);
        Instant expiresAt = now.plus(options.defaultBrowserFlowTtl());
        flows.put(flowId, new BrowserFlow(
                request.providerId(),
                request.baseUrl().or(() -> options.baseUrl()),
                request.metadata(),
                redirectUri,
                verifier,
                state,
                expiresAt));
        browserFlowIdsByState.put(state, flowId);
        URI authorizationUri = authorizationUri(request, redirectUri, state, verifier);
        return new SubscriptionLoginStart(
                request.providerId(),
                SubscriptionLoginMode.BROWSER,
                flowId,
                authorizationUri,
                Optional.empty(),
                Optional.empty(),
                Optional.of(expiresAt),
                request.metadata());
    }

    @Override
    public SubscriptionLoginStart startDeviceCodeLogin(DeviceCodeSubscriptionLoginRequest request, Instant now) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(now, "now");
        URI endpoint = options.deviceAuthorizationEndpoint()
                .orElseThrow(() -> new IllegalStateException(
                        "deviceAuthorizationEndpoint is required for device-code subscription login"));
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", options.clientId());
        putIfPresent(form, "scope", Optional.of(options.scope()).filter(value -> !value.isBlank()));
        request.metadata().forEach((key, value) -> form.put("metadata_" + key, value));
        JsonNode body = postForm(endpoint, form);
        String flowId = token(24);
        String deviceCode = requiredText(body, "device_code");
        URI verificationUri = URI.create(text(body, "verification_uri")
                .or(() -> text(body, "verification_url"))
                .orElseThrow(() -> new IllegalStateException("device login response is missing verification_uri")));
        Optional<URI> verificationComplete = text(body, "verification_uri_complete").map(URI::create);
        String userCode = requiredText(body, "user_code");
        long expiresIn = longValue(body, "expires_in").orElse(600L);
        long intervalSeconds = longValue(body, "interval").orElse(5L);
        Instant expiresAt = now.plusSeconds(expiresIn);
        flows.put(flowId, new DeviceFlow(
                request.providerId(),
                request.baseUrl().or(() -> options.baseUrl()),
                request.metadata(),
                deviceCode,
                intervalSeconds,
                expiresAt));
        return new SubscriptionLoginStart(
                request.providerId(),
                SubscriptionLoginMode.DEVICE_CODE,
                flowId,
                verificationComplete.orElse(verificationUri),
                Optional.of(verificationUri),
                Optional.of(userCode),
                Optional.of(expiresAt),
                request.metadata());
    }

    @Override
    public SubscriptionLoginPollResult pollLogin(String flowId, Instant now) {
        Objects.requireNonNull(flowId, "flowId");
        Objects.requireNonNull(now, "now");
        Flow flow = flows.get(flowId);
        if (flow == null) {
            return SubscriptionLoginPollResult.failed("unknown subscription login flow: " + flowId);
        }
        if (!flow.expiresAt().isAfter(now)) {
            removeFlow(flowId, flow);
            return SubscriptionLoginPollResult.expired("subscription login expired");
        }
        if (flow instanceof BrowserFlow) {
            return SubscriptionLoginPollResult.pending(Optional.empty());
        }
        DeviceFlow device = (DeviceFlow) flow;
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", DEVICE_CODE_GRANT);
        form.put("device_code", device.deviceCode());
        form.put("client_id", options.clientId());
        JsonNode body = postForm(options.tokenEndpoint(), form);
        Optional<String> error = text(body, "error");
        if (error.isPresent()) {
            return switch (error.orElseThrow()) {
                case "authorization_pending" -> SubscriptionLoginPollResult.pending(
                        Optional.of(now.plusSeconds(device.intervalSeconds())));
                case "slow_down" -> SubscriptionLoginPollResult.pending(
                        Optional.of(now.plusSeconds(device.intervalSeconds() + 5)));
                case "expired_token" -> {
                    removeFlow(flowId, flow);
                    yield SubscriptionLoginPollResult.expired("device code expired");
                }
                default -> {
                    removeFlow(flowId, flow);
                    yield SubscriptionLoginPollResult.failed(text(body, "error_description").orElse(error.orElseThrow()));
                }
            };
        }
        SubscriptionLoginCompletion completion = completion(flowId, device, body, now);
        removeFlow(flowId, flow);
        return SubscriptionLoginPollResult.completed(completion);
    }

    @Override
    public SubscriptionLoginPollResult completeBrowserLoginCallback(String code, String state, Instant now) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(now, "now");
        String flowId = browserFlowIdsByState.get(state);
        if (flowId == null) {
            return SubscriptionLoginPollResult.failed("unknown browser subscription login state");
        }
        return completeBrowserLogin(flowId, code, state, now);
    }

    public SubscriptionLoginPollResult completeBrowserLogin(String flowId, String code, String state, Instant now) {
        Objects.requireNonNull(flowId, "flowId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(now, "now");
        Flow flow = flows.get(flowId);
        if (!(flow instanceof BrowserFlow browser)) {
            return SubscriptionLoginPollResult.failed("unknown browser subscription login flow: " + flowId);
        }
        if (!browser.expiresAt().isAfter(now)) {
            removeFlow(flowId, flow);
            return SubscriptionLoginPollResult.expired("subscription login expired");
        }
        if (!state.equals(browser.state())) {
            removeFlow(flowId, flow);
            return SubscriptionLoginPollResult.failed("subscription login state mismatch");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("client_id", options.clientId());
        form.put("redirect_uri", browser.redirectUri().toString());
        form.put("code_verifier", browser.codeVerifier());
        JsonNode body = postForm(options.tokenEndpoint(), form);
        Optional<String> error = text(body, "error");
        if (error.isPresent()) {
            removeFlow(flowId, flow);
            return SubscriptionLoginPollResult.failed(text(body, "error_description").orElse(error.orElseThrow()));
        }
        SubscriptionLoginCompletion completion = completion(flowId, browser, body, now);
        removeFlow(flowId, flow);
        return SubscriptionLoginPollResult.completed(completion);
    }

    @Override
    public Optional<SubscriptionLoginCompletion> refreshLogin(AuthSession session, Instant now) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(now, "now");
        Optional<String> refreshToken = Optional.ofNullable(session.auth().metadata().get("refreshToken"))
                .filter(value -> !value.isBlank());
        if (refreshToken.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken.orElseThrow());
        form.put("client_id", options.clientId());
        putIfPresent(form, "scope", Optional.of(options.scope()).filter(value -> !value.isBlank()));
        JsonNode body = postForm(options.tokenEndpoint(), form);
        Optional<String> error = text(body, "error");
        if (error.isPresent()) {
            return Optional.empty();
        }
        RefreshFlow flow = new RefreshFlow(
                session.providerId(),
                session.auth().baseUrl(),
                session.auth().metadata(),
                session.expiresAt().orElse(now));
        return Optional.of(completion("refresh-" + session.providerId(), flow, body, now));
    }

    private void removeFlow(String flowId, Flow flow) {
        flows.remove(flowId);
        if (flow instanceof BrowserFlow browser) {
            browserFlowIdsByState.remove(browser.state(), flowId);
        }
    }

    private URI authorizationUri(BrowserSubscriptionLoginRequest request, URI redirectUri, String state, String verifier) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("response_type", "code");
        query.put("client_id", options.clientId());
        query.put("redirect_uri", redirectUri.toString());
        query.put("state", state);
        query.put("code_challenge", codeChallenge(verifier));
        query.put("code_challenge_method", "S256");
        putIfPresent(query, "scope", Optional.of(options.scope()).filter(value -> !value.isBlank()));
        query.put("use_hosted_login_success_page", Boolean.toString(options.useHostedLoginSuccessPage()));
        query.put("app_brand", options.appBrand());
        options.authorizationParameters().forEach(query::putIfAbsent);
        request.metadata().forEach((key, value) -> query.put("metadata_" + key, value));
        return URI.create(options.authorizationEndpoint() + "?" + encode(query));
    }

    private SubscriptionLoginCompletion completion(String flowId, Flow flow, JsonNode body, Instant now) {
        String accessToken = requiredText(body, "access_token");
        Optional<String> baseUrl = text(body, "base_url").or(flow::baseUrl);
        Optional<Instant> expiresAt = longValue(body, "expires_in")
                .map(now::plusSeconds)
                .or(() -> text(body, "expires_at").map(Instant::parse));
        Map<String, String> metadata = new LinkedHashMap<>(flow.metadata());
        text(body, "refresh_token").ifPresent(value -> metadata.put("refreshToken", value));
        text(body, "token_type").ifPresent(value -> metadata.put("tokenType", value));
        text(body, "scope").ifPresent(value -> metadata.put("scope", value));
        text(body, "plan").ifPresent(value -> metadata.put("plan", value));
        text(body, "plan_type").ifPresent(value -> metadata.put("plan", value));
        text(body, "account_id").ifPresent(value -> metadata.put("accountId", value));
        return new SubscriptionLoginCompletion(
                flow.providerId(),
                flowId,
                accessToken,
                baseUrl,
                expiresAt,
                metadata);
    }

    private JsonNode postForm(URI endpoint, Map<String, String> form) {
        try {
            return transport.postForm(endpoint, form, options.headers());
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI subscription login request failed", e);
        }
    }

    private static void putIfPresent(Map<String, String> values, String key, Optional<String> value) {
        value.ifPresent(present -> values.put(key, present));
    }

    private static String requiredText(JsonNode body, String field) {
        return text(body, field)
                .orElseThrow(() -> new IllegalStateException("OpenAI subscription login response is missing " + field));
    }

    private static Optional<String> text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (!value.isTextual()) {
            throw new IllegalStateException("OpenAI subscription login response field must be text: " + field);
        }
        return Optional.of(value.asText());
    }

    private static Optional<Long> longValue(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (!value.canConvertToLong()) {
            throw new IllegalStateException("OpenAI subscription login response field must be numeric: " + field);
        }
        return Optional.of(value.asLong());
    }

    private static String codeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException("failed to create OAuth PKCE challenge", e);
        }
    }

    private static String token(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String encode(Map<String, String> form) {
        StringBuilder builder = new StringBuilder();
        form.forEach((key, value) -> {
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(urlEncode(key)).append('=').append(urlEncode(value));
        });
        return builder.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private sealed interface Flow permits BrowserFlow, DeviceFlow, RefreshFlow {
        String providerId();

        Optional<String> baseUrl();

        Map<String, String> metadata();

        Instant expiresAt();
    }

    private record BrowserFlow(
            String providerId,
            Optional<String> baseUrl,
            Map<String, String> metadata,
            URI redirectUri,
            String codeVerifier,
            String state,
            Instant expiresAt
    ) implements Flow {
        private BrowserFlow {
            metadata = Map.copyOf(metadata);
        }
    }

    private record DeviceFlow(
            String providerId,
            Optional<String> baseUrl,
            Map<String, String> metadata,
            String deviceCode,
            long intervalSeconds,
            Instant expiresAt
    ) implements Flow {
        private DeviceFlow {
            metadata = Map.copyOf(metadata);
        }
    }

    private record RefreshFlow(
            String providerId,
            Optional<String> baseUrl,
            Map<String, String> metadata,
            Instant expiresAt
    ) implements Flow {
        private RefreshFlow {
            metadata = Map.copyOf(metadata);
        }
    }
}
