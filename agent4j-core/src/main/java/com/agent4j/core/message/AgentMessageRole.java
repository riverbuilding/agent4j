package com.agent4j.core.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AgentMessageRole {
    USER("user"),
    ASSISTANT("assistant"),
    TOOL_RESULT("toolResult"),
    BASH_EXECUTION("bashExecution"),
    CUSTOM("custom"),
    BRANCH_SUMMARY("branchSummary"),
    COMPACTION_SUMMARY("compactionSummary"),
    SYSTEM("system"),
    UNKNOWN("unknown");

    private final String wireName;

    AgentMessageRole(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static AgentMessageRole fromWireName(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (AgentMessageRole role : values()) {
            if (role.wireName.equals(value)) {
                return role;
            }
        }
        return UNKNOWN;
    }
}
