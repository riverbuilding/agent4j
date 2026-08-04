package com.agent4j.coding.message;

final class PromptMarkup {
    private PromptMarkup() {
    }

    static String attribute(String value) {
        String normalized = value == null ? "" : value;
        return normalized
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
