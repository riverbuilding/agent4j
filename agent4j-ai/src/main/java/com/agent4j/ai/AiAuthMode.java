package com.agent4j.ai;

import java.util.Locale;
import java.util.Objects;

public enum AiAuthMode {
    NONE("none"),
    API_KEY("api-key"),
    ACCESS_TOKEN("access-token"),
    CHATGPT_SUBSCRIPTION("chatgpt-subscription"),
    CUSTOM_HEADERS("custom-headers");

    private final String wireName;

    AiAuthMode(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static AiAuthMode fromWireName(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        String normalized = wireName.strip().toLowerCase(Locale.ROOT);
        for (AiAuthMode mode : values()) {
            if (mode.wireName.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("unknown auth mode: " + wireName);
    }
}
