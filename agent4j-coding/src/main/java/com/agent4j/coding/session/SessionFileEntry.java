package com.agent4j.coding.session;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public record SessionFileEntry(String path, JsonNode payload) {
    public Optional<String> optionalPath() {
        return Optional.ofNullable(path);
    }
}
