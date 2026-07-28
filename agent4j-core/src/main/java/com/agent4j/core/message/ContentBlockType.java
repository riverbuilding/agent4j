package com.agent4j.core.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ContentBlockType {
    TEXT("text"),
    REASONING("reasoning"),
    TOOL_CALL("toolCall"),
    UNKNOWN("unknown");

    private final String wireName;

    ContentBlockType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static ContentBlockType fromWireName(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (ContentBlockType type : values()) {
            if (type.wireName.equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
