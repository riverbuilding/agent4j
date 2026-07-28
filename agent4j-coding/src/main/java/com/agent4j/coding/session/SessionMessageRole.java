package com.agent4j.coding.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SessionMessageRole {
    USER("user"),
    ASSISTANT("assistant"),
    TOOL_RESULT("toolResult"),
    BASH_EXECUTION("bashExecution"),
    CUSTOM("custom"),
    BRANCH_SUMMARY("branchSummary"),
    COMPACTION_SUMMARY("compactionSummary"),
    UNKNOWN("unknown");

    private final String wireName;

    SessionMessageRole(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static SessionMessageRole fromWireName(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (SessionMessageRole role : values()) {
            if (role.wireName.equals(value)) {
                return role;
            }
        }
        return UNKNOWN;
    }
}
