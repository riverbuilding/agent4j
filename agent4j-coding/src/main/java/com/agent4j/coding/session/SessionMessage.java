package com.agent4j.coding.session;

import com.fasterxml.jackson.databind.JsonNode;

public record SessionMessage(SessionMessageRole role, JsonNode content, JsonNode payload) {
}
