package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderFeatures;
import com.agent4j.ai.AiProviderRequestHook;
import com.agent4j.ai.openai.DefaultOpenAiTransport;
import com.agent4j.ai.openai.OpenAiResponsesProvider;
import com.agent4j.ai.openai.OpenAiResponsesProviderOptions;

import java.util.ArrayList;
import java.util.List;

/** Creates OpenAI Responses transports from compatible-provider catalog data. */
public final class OpenAiCompatibleProviderAdapter {
    private OpenAiCompatibleProviderAdapter() {
    }

    public static AiProvider create(OpenAiCompatibleProviderConfig config, AiModelReference selectedModel) {
        List<AiModel> models = new ArrayList<>(config.models().stream()
                .map(model -> model.withBaseUrl(config.baseUrl().toString()))
                .toList());
        if (selectedModel.providerId().equals(config.id()) && models.stream().noneMatch(model -> model.id().equals(selectedModel.modelId()))) {
            models.add(new AiModel(selectedModel, selectedModel.modelId()).withBaseUrl(config.baseUrl().toString()));
        }
        return new OpenAiResponsesProvider(new OpenAiResponsesProviderOptions(
                config.id(), config.name(), config.baseUrl(), models, config.headers(),
                AiProviderRequestHook.identity(), AiProviderFeatures.defaults()), new DefaultOpenAiTransport());
    }
}
