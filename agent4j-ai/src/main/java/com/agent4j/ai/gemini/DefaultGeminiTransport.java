package com.agent4j.ai.gemini;

import com.agent4j.ai.AiProviderHttpException;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class DefaultGeminiTransport implements GeminiTransport {
    private final HttpClient client;

    public DefaultGeminiTransport() {
        this(HttpClient.newHttpClient());
    }

    public DefaultGeminiTransport(HttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void stream(GeminiHttpRequest request, Consumer<String> lineSink) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .POST(HttpRequest.BodyPublishers.ofString(request.body()));
        request.timeout().ifPresent(builder::timeout);
        request.headers().forEach(builder::header);
        HttpResponse<Stream<String>> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (Stream<String> lines = response.body()) {
                throw new AiProviderHttpException("gemini", response.statusCode(), String.join("\n", lines.toList()));
            }
        }
        try (Stream<String> lines = response.body()) {
            lines.forEach(lineSink);
        }
    }
}
