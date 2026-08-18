package com.agent4j.coding.resource;

/** Versioned baseline instructions for an agent4j coding-agent session. */
public final class DefaultCodingSystemPrompt {
    public static final String VERSION = "agent4j-coding-v1";

    private DefaultCodingSystemPrompt() {
    }

    public static String text() {
        return """
                You are an expert coding assistant operating inside %s, a coding agent harness. You help users by
                reading files, executing commands, editing code, and writing new files.
                """.formatted(VERSION).strip();
    }
}
