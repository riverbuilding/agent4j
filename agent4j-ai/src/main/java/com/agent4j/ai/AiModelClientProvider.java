package com.agent4j.ai;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Adapts a fixed-model client to the provider transport abstraction. */
public final class AiModelClientProvider implements AiProvider {
    private final AiModel model;
    private final AiModelClient client;

    public AiModelClientProvider(AiModel model, AiModelClient client) {
        this.model = Objects.requireNonNull(model, "model");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public String id() {
        return model.providerId();
    }

    @Override
    public String name() {
        return model.name();
    }

    @Override
    public AiProviderApi api() {
        return AiProviderApi.CUSTOM;
    }

    @Override
    public List<AiModel> models() {
        return List.of(model);
    }

    @Override
    public void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) throws Exception {
        client.stream(request.turn(), sink);
    }
}
