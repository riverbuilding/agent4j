package com.agent4j.coding.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AgentSettings(ObjectNode values) {
    public AgentSettings {
        Objects.requireNonNull(values, "values");
        values = values.deepCopy();
    }

    public Optional<JsonNode> field(String fieldName) {
        return values.has(fieldName) ? Optional.of(values.get(fieldName)) : Optional.empty();
    }

    public Optional<String> textField(String fieldName) {
        return field(fieldName)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText);
    }

    public Optional<Boolean> booleanField(String fieldName) {
        return field(fieldName)
                .filter(JsonNode::isBoolean)
                .map(JsonNode::asBoolean);
    }

    public Optional<Integer> intField(String fieldName) {
        return field(fieldName)
                .filter(JsonNode::canConvertToInt)
                .map(JsonNode::asInt);
    }

    public List<String> textArrayField(String fieldName) {
        return field(fieldName)
                .filter(JsonNode::isArray)
                .map(array -> {
                    List<String> values = new ArrayList<>();
                    array.forEach(value -> {
                        if (value.isTextual()) {
                            values.add(value.asText());
                        }
                    });
                    return List.copyOf(values);
                })
                .orElseGet(List::of);
    }
}
