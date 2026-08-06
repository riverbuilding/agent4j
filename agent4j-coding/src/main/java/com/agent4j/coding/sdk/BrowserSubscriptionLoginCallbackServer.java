package com.agent4j.coding.sdk;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class BrowserSubscriptionLoginCallbackServer implements AutoCloseable {
    private static final String DEFAULT_PATH = "/auth/callback";

    private final HttpServer server;
    private final String callbackPath;
    private final CompletableFuture<SubscriptionLoginPollResult> completion = new CompletableFuture<>();

    private BrowserSubscriptionLoginCallbackServer(
            HttpServer server,
            String callbackPath,
            LoginService loginService
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.callbackPath = normalizePath(callbackPath);
        Objects.requireNonNull(loginService, "loginService");
        server.createContext(this.callbackPath, exchange -> handle(exchange, loginService));
    }

    public static BrowserSubscriptionLoginCallbackServer start(LoginService loginService) throws IOException {
        return start(InetAddress.getLoopbackAddress(), 0, DEFAULT_PATH, null, loginService);
    }

    public static BrowserSubscriptionLoginCallbackServer startDefaultBrowserCallback(
            LoginService loginService
    ) throws IOException {
        return start(InetAddress.getLoopbackAddress(), 1455, DEFAULT_PATH, null, loginService);
    }

    public static BrowserSubscriptionLoginCallbackServer start(
            InetAddress bindAddress,
            int port,
            String callbackPath,
            Executor executor,
            LoginService loginService
    ) throws IOException {
        Objects.requireNonNull(bindAddress, "bindAddress");
        HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        if (executor != null) {
            server.setExecutor(executor);
        }
        BrowserSubscriptionLoginCallbackServer callbackServer =
                new BrowserSubscriptionLoginCallbackServer(server, callbackPath, loginService);
        server.start();
        return callbackServer;
    }

    public URI redirectUri() {
        InetSocketAddress address = server.getAddress();
        String host = address.getAddress().isLoopbackAddress() ? "localhost" : address.getHostString();
        return URI.create("http://" + host + ":" + address.getPort() + callbackPath);
    }

    public CompletableFuture<SubscriptionLoginPollResult> completion() {
        return completion;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange, LoginService loginService) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "Method not allowed");
                return;
            }
            Map<String, String> query = query(exchange.getRequestURI());
            Optional<String> error = Optional.ofNullable(query.get("error"));
            if (error.isPresent()) {
                SubscriptionLoginPollResult result = SubscriptionLoginPollResult.failed(
                        Optional.ofNullable(query.get("error_description")).orElse(error.orElseThrow()));
                completion.complete(result);
                send(exchange, 400, "Login failed. You can close this window.");
                return;
            }
            String code = query.get("code");
            String state = query.get("state");
            if (code == null || code.isBlank() || state == null || state.isBlank()) {
                SubscriptionLoginPollResult result = SubscriptionLoginPollResult.failed(
                        "browser login callback is missing code or state");
                completion.complete(result);
                send(exchange, 400, "Login failed. You can close this window.");
                return;
            }
            SubscriptionLoginPollResult result = loginService.completeBrowserSubscriptionLoginCallback(code, state);
            completion.complete(result);
            if (result.status() == SubscriptionLoginStatus.COMPLETED) {
                send(exchange, 200, "Login complete. You can close this window.");
            } else {
                send(exchange, 400, "Login failed. You can close this window.");
            }
        } catch (RuntimeException e) {
            completion.complete(SubscriptionLoginPollResult.failed(e.getMessage()));
            send(exchange, 500, "Login failed. You can close this window.");
        } finally {
            exchange.close();
        }
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new LinkedHashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        for (String part : rawQuery.split("&")) {
            int separator = part.indexOf('=');
            String key = separator < 0 ? part : part.substring(0, separator);
            String value = separator < 0 ? "" : part.substring(separator + 1);
            values.put(decode(key), decode(value));
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static String normalizePath(String path) {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) {
            throw new IllegalArgumentException("callbackPath must not be blank");
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
