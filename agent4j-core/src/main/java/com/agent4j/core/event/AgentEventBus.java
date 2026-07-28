package com.agent4j.core.event;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class AgentEventBus {
    private final CopyOnWriteArrayList<Consumer<AgentEvent>> subscribers = new CopyOnWriteArrayList<>();

    public EventSubscription subscribe(Consumer<AgentEvent> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    public void publish(AgentEvent event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<AgentEvent> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }

    public int subscriberCount() {
        return subscribers.size();
    }
}
