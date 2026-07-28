package com.agent4j.coding.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SessionTree {
    private final Map<String, SessionEntry> entriesById;
    private final Map<String, List<SessionEntry>> childrenByParentId;

    private SessionTree(Map<String, SessionEntry> entriesById, Map<String, List<SessionEntry>> childrenByParentId) {
        this.entriesById = Map.copyOf(entriesById);
        this.childrenByParentId = childrenByParentId.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())));
    }

    public static SessionTree from(SessionDocument document) {
        Objects.requireNonNull(document, "document");
        Map<String, SessionEntry> entriesById = new HashMap<>();
        Map<String, List<SessionEntry>> childrenByParentId = new HashMap<>();
        for (SessionEntry entry : document.entries()) {
            if (entry.id() == null) {
                continue;
            }
            SessionEntry previous = entriesById.put(entry.id(), entry);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate session entry id: " + entry.id());
            }
            if (entry.parentId() != null) {
                childrenByParentId.computeIfAbsent(entry.parentId(), ignored -> new ArrayList<>()).add(entry);
            }
        }
        return new SessionTree(entriesById, childrenByParentId);
    }

    public SessionEntry requireEntry(String id) {
        SessionEntry entry = entriesById.get(id);
        if (entry == null) {
            throw new IllegalArgumentException("unknown session entry id: " + id);
        }
        return entry;
    }

    public List<SessionEntry> childrenOf(String parentId) {
        return childrenByParentId.getOrDefault(parentId, List.of());
    }

    public List<SessionEntry> activePathTo(String id) {
        List<SessionEntry> reversed = new ArrayList<>();
        SessionEntry current = requireEntry(id);
        while (current != null) {
            reversed.add(current);
            current = current.parentId() == null ? null : entriesById.get(current.parentId());
        }
        return reversed.reversed();
    }
}
