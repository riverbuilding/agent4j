package com.agent4j.testkit.ai;

import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderApi;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

public final class FakeProvider implements AiProvider {
    private final String id;
    private final String name;
    private final AiProviderApi api;
    private final List<AiModel> models;
    private final Queue<Object> turns = new ArrayDeque<>();
    private final List<AiProviderRequest> requests = new ArrayList<>();

    public FakeProvider(String id, String name, AiProviderApi api, List<AiModel> models) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.api = Objects.requireNonNull(api, "api");
        this.models = List.copyOf(Objects.requireNonNull(models, "models"));
    }

    public FakeProvider enqueue(List<AiStreamEvent> events) {
        turns.add(List.copyOf(Objects.requireNonNull(events, "events")));
        return this;
    }

    public FakeProvider enqueueFailure(RuntimeException failure) {
        turns.add(Objects.requireNonNull(failure, "failure"));
        return this;
    }

    public List<AiProviderRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AiProviderApi api() {
        return api;
    }

    @Override
    public List<AiModel> models() {
        return models;
    }

    @Override
    public void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) {
        requests.add(request);
        request.options().signal().throwIfAborted();
        Object turn = turns.poll();
        if (turn == null) {
            throw new IllegalStateException("no fake provider turn enqueued");
        }
        if (turn instanceof RuntimeException failure) {
            throw failure;
        }
        @SuppressWarnings("unchecked")
        List<AiStreamEvent> events = (List<AiStreamEvent>) turn;
        for (AiStreamEvent event : events) {
            request.options().signal().throwIfAborted();
            sink.accept(event);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
