package com.agent4j.coding.tool;

public record TruncatedText(String text, boolean truncated, int originalLength) {
    public static TruncatedText of(String text, int maxChars) {
        String normalized = text == null ? "" : text;
        if (normalized.length() <= maxChars) {
            return new TruncatedText(normalized, false, normalized.length());
        }
        return new TruncatedText(normalized.substring(0, maxChars), true, normalized.length());
    }
}
