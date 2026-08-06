package com.agent4j.coding.sdk;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.Map;

public interface OpenAiSubscriptionLoginHttpTransport {
    JsonNode postForm(URI endpoint, Map<String, String> form, Map<String, String> headers) throws Exception;
}
