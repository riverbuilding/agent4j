package com.agent4j.coding.session;

import com.fasterxml.jackson.databind.JsonNode;

public record SessionThinkingLevelChange(String thinkingLevel, JsonNode payload) {
}
