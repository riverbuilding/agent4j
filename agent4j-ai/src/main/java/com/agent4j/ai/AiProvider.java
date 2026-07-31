package com.agent4j.ai;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public interface AiProvider {
    String id();

    String name();

    AiProviderApi api();

    List<AiModel> models();

    void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) throws Exception;

    default Optional<AiModel> model(String modelId) {
        Objects.requireNonNull(modelId, "modelId");
        return models().stream()
                .filter(model -> model.id().equals(modelId))
                .findFirst();
    }
}
