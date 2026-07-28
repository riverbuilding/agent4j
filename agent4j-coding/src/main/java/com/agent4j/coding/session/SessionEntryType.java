package com.agent4j.coding.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum SessionEntryType {
    SESSION("session"),
    MESSAGE("message"),
    MODEL_CHANGE("model_change"),
    THINKING_LEVEL_CHANGE("thinking_level_change"),
    COMPACTION("compaction"),
    SESSION_INFO("session_info"),
    FILE("file"),
    CUSTOM("custom"),
    UNKNOWN("unknown");

    private final String wireName;

    SessionEntryType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static SessionEntryType fromWireName(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (SessionEntryType type : values()) {
            if (type.wireName.equals(normalized)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
