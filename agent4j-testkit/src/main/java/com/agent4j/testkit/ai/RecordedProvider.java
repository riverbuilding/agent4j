package com.agent4j.testkit.ai;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderApi;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStreamEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class RecordedProvider implements AiProvider {
    private final RecordedProviderFixture fixture;
    private final List<AiProviderRequest> requests = new ArrayList<>();

    public RecordedProvider(RecordedProviderFixture fixture) {
        this.fixture = Objects.requireNonNull(fixture, "fixture");
    }

    public List<AiProviderRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public String id() {
        return fixture.providerId();
    }

    @Override
    public String name() {
        return fixture.providerName();
    }

    @Override
    public AiProviderApi api() {
        return fixture.api();
    }

    @Override
    public List<AiModel> models() {
        return List.of(fixture.model());
    }

    @Override
    public void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) {
        requests.add(request);
        for (AiStreamEvent event : fixture.events()) {
            request.options().signal().throwIfAborted();
            sink.accept(event);
        }
    }
}
