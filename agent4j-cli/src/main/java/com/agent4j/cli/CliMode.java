package com.agent4j.cli;

public enum CliMode {
    TEXT("text"),
    JSON("json"),
    RPC("rpc");

    private final String wireName;

    CliMode(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static CliMode fromWireName(String value) {
        for (CliMode mode : values()) {
            if (mode.wireName.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("expected one of: text, json, rpc");
    }
}
