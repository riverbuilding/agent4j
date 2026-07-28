package com.agent4j.core.event;

@FunctionalInterface
public interface EventSubscription extends AutoCloseable {
    @Override
    void close();
}
