package com.agent4j.coding.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class DefaultOpenAiSubscriptionLoginHttpTransport implements OpenAiSubscriptionLoginHttpTransport {
    private final HttpClient client;
    private final ObjectMapper mapper;

    public DefaultOpenAiSubscriptionLoginHttpTransport() {
        this(HttpClient.newHttpClient(), new ObjectMapper());
    }

    public DefaultOpenAiSubscriptionLoginHttpTransport(HttpClient client, ObjectMapper mapper) {
        this.client = Objects.requireNonNull(client, "client");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public JsonNode postForm(URI endpoint, Map<String, String> form, Map<String, String> headers) throws Exception {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(headers, "headers");
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form)));
        headers.forEach(builder::header);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode body = response.body() == null || response.body().isBlank()
                ? mapper.createObjectNode()
                : mapper.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI subscription login request failed: HTTP "
                    + response.statusCode() + " " + body);
        }
        return body;
    }

    private static String encode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
