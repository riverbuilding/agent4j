package com.agent4j.testkit.ai;

import com.agent4j.ai.AiModelClient;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTurnRequest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

public final class FakeModelClient implements AiModelClient {
    private final Queue<Object> turns = new ArrayDeque<>();
    private final List<AiTurnRequest> requests = new ArrayList<>();

    public FakeModelClient enqueue(List<AiStreamEvent> events) {
        turns.add(List.copyOf(Objects.requireNonNull(events, "events")));
        return this;
    }

    public FakeModelClient enqueueFailure(RuntimeException failure) {
        turns.add(Objects.requireNonNull(failure, "failure"));
        return this;
    }

    public List<AiTurnRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public void stream(AiTurnRequest request, Consumer<AiStreamEvent> sink) {
        requests.add(request);
        Object turn = turns.poll();
        if (turn == null) {
            throw new IllegalStateException("no fake model turn enqueued");
        }
        if (turn instanceof RuntimeException failure) {
            throw failure;
        }
        @SuppressWarnings("unchecked")
        List<AiStreamEvent> events = (List<AiStreamEvent>) turn;
        events.forEach(sink);
    }
}
