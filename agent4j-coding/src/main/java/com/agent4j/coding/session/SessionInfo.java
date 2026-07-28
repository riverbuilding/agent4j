package com.agent4j.coding.session;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public record SessionInfo(String name, JsonNode payload) {
    public Optional<String> optionalName() {
        return Optional.ofNullable(name);
    }
}
