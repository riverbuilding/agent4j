package com.agent4j.coding.tool;

import java.util.List;

/** Named built-in tool sets for coding and inspection agent modes. */
public enum CodingToolProfile {
    DEFAULT_CODING(List.of("read", "write", "edit", "bash")),
    READ_ONLY(List.of("read", "grep", "find", "ls")),
    FULL(List.of("read", "write", "edit", "bash", "ls", "grep", "find"));

    private final List<String> toolNames;

    CodingToolProfile(List<String> toolNames) {
        this.toolNames = List.copyOf(toolNames);
    }

    List<String> toolNames() {
        return toolNames;
    }
}
