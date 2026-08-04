package com.agent4j.ai.anthropic;

import com.agent4j.ai.AiProviderHttpException;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class DefaultAnthropicTransport implements AnthropicTransport {
    private final HttpClient client;

    public DefaultAnthropicTransport() {
        this(HttpClient.newHttpClient());
    }

    public DefaultAnthropicTransport(HttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void stream(AnthropicHttpRequest request, Consumer<String> lineSink) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .POST(HttpRequest.BodyPublishers.ofString(request.body()));
        request.timeout().ifPresent(builder::timeout);
        request.headers().forEach(builder::header);

        HttpResponse<Stream<String>> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body;
            try (Stream<String> lines = response.body()) {
                body = String.join("\n", lines.toList());
            }
            throw new AiProviderHttpException("anthropic", response.statusCode(), body);
        }
        try (Stream<String> lines = response.body()) {
            lines.forEach(lineSink);
        }
    }
}
