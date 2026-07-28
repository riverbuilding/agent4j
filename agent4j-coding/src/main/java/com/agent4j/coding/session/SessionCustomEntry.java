package com.agent4j.coding.session;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public record SessionCustomEntry(String customType, JsonNode payload) {
    public Optional<String> optionalCustomType() {
        return Optional.ofNullable(customType);
    }
}
