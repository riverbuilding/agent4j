package com.agent4j.coding.sdk;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryAuthCredentialStore implements AuthCredentialStore {
    private final Map<String, AuthSession> sessions = new LinkedHashMap<>();

    @Override
    public synchronized Optional<AuthSession> find(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return Optional.ofNullable(sessions.get(providerId));
    }

    @Override
    public synchronized void save(AuthSession session) {
        Objects.requireNonNull(session, "session");
        sessions.put(session.providerId(), session);
    }

    @Override
    public synchronized boolean delete(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return sessions.remove(providerId) != null;
    }
}
