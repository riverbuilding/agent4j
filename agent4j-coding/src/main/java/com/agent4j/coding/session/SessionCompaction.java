package com.agent4j.coding.session;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public record SessionCompaction(JsonNode summary, JsonNode retainedEntries, JsonNode payload) {
    public Optional<JsonNode> optionalSummary() {
        return summary == null || summary.isNull() ? Optional.empty() : Optional.of(summary);
    }

    public Optional<JsonNode> optionalRetainedEntries() {
        return retainedEntries == null || retainedEntries.isNull() ? Optional.empty() : Optional.of(retainedEntries);
    }
}
