package com.agent4j.core.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum QueueKind {
    STEER("steer"),
    FOLLOW_UP("followUp");

    private final String wireName;

    QueueKind(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static QueueKind fromWireName(String value) {
        for (QueueKind kind : values()) {
            if (kind.wireName.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown queue kind: " + value);
    }
}
