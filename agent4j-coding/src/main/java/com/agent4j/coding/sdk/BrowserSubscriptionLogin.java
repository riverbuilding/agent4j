package com.agent4j.coding.sdk;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Owns a local browser-login callback server and its in-flight OAuth result. */
public final class BrowserSubscriptionLogin implements AutoCloseable {
    private final BrowserSubscriptionLoginCallbackServer callbackServer;
    private final SubscriptionLoginStart start;

    private BrowserSubscriptionLogin(
            BrowserSubscriptionLoginCallbackServer callbackServer,
            SubscriptionLoginStart start
    ) {
        this.callbackServer = callbackServer;
        this.start = start;
    }

    public static BrowserSubscriptionLogin start(
            LoginService loginService,
            BrowserSubscriptionLoginRequest request,
            BrowserLauncher launcher
    ) throws IOException {
        Objects.requireNonNull(loginService, "loginService");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(launcher, "launcher");

        BrowserSubscriptionLoginCallbackServer callbackServer =
                BrowserSubscriptionLoginCallbackServer.startDefaultBrowserCallback(loginService);
        try {
            Map<String, String> metadata = new LinkedHashMap<>(request.metadata());
            URI redirectUri = callbackServer.redirectUri();
            metadata.put("redirectUri", redirectUri.toString());
            BrowserSubscriptionLoginRequest callbackRequest = new BrowserSubscriptionLoginRequest(
                    request.providerId(), request.baseUrl(), metadata);
            SubscriptionLoginStart start = loginService.startBrowserSubscriptionLogin(callbackRequest);
            launcher.open(start.authorizationUri());
            return new BrowserSubscriptionLogin(callbackServer, start);
        } catch (RuntimeException | IOException e) {
            callbackServer.close();
            throw e;
        }
    }

    public SubscriptionLoginStart start() {
        return start;
    }

    public CompletableFuture<SubscriptionLoginPollResult> completion() {
        return callbackServer.completion();
    }

    public URI redirectUri() {
        return callbackServer.redirectUri();
    }

    @Override
    public void close() {
        callbackServer.close();
    }
}
