package com.agent4j.core.compaction;

public enum CompactionReason {
    MANUAL("manual"),
    THRESHOLD("threshold"),
    OVERFLOW("overflow");

    private final String wireName;

    CompactionReason(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
