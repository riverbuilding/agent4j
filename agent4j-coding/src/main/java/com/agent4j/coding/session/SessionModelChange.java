package com.agent4j.coding.session;

import com.fasterxml.jackson.databind.JsonNode;

public record SessionModelChange(String provider, String modelId, JsonNode payload) {
}
