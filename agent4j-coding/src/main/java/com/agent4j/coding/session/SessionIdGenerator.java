package com.agent4j.coding.session;

import java.security.SecureRandom;

@FunctionalInterface
public interface SessionIdGenerator {
    String nextId();

    static SessionIdGenerator randomHex() {
        SecureRandom random = new SecureRandom();
        return () -> "%08x".formatted(random.nextInt());
    }
}
