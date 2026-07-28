package com.agent4j.coding.session;

import java.util.List;
import java.util.Objects;

public record SessionDocument(SessionEntry header, List<SessionEntry> entries) {
    public SessionDocument {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(entries, "entries");
        if (!header.isHeader()) {
            throw new IllegalArgumentException("first JSONL entry must be a session header");
        }
        entries = List.copyOf(entries);
    }

    public List<SessionEntry> allEntries() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(header), entries.stream()).toList();
    }
}
