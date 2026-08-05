package com.agent4j.coding.sdk;

import java.util.Optional;

public interface AuthCredentialStore {
    Optional<AuthSession> find(String providerId);

    void save(AuthSession session);

    boolean delete(String providerId);
}
