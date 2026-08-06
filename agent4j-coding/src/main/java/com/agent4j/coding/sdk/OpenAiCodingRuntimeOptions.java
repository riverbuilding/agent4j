package com.agent4j.coding.sdk;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.openai.DefaultOpenAiTransport;
import com.agent4j.ai.openai.OpenAiResponsesProviderOptions;
import com.agent4j.ai.openai.OpenAiTransport;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record OpenAiCodingRuntimeOptions(
        List<AiModel> models,
        AiModelReference defaultModel,
        AuthCredentialStore credentialStore,
        Optional<OpenAiSubscriptionLoginClientOptions> subscriptionLogin,
        OpenAiSubscriptionLoginHttpTransport subscriptionLoginTransport,
        OpenAiResponsesProviderOptions responsesProvider,
        OpenAiTransport responsesTransport,
        Clock clock
) {
    public OpenAiCodingRuntimeOptions {
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(defaultModel, "defaultModel");
        Objects.requireNonNull(credentialStore, "credentialStore");
        subscriptionLogin = subscriptionLogin == null ? Optional.empty() : subscriptionLogin;
        Objects.requireNonNull(subscriptionLoginTransport, "subscriptionLoginTransport");
        Objects.requireNonNull(responsesProvider, "responsesProvider");
        Objects.requireNonNull(responsesTransport, "responsesTransport");
        Objects.requireNonNull(clock, "clock");
        models = List.copyOf(models);
        if (models.isEmpty()) {
            throw new IllegalArgumentException("models must not be empty");
        }
        if (models.stream().noneMatch(model -> model.reference().equals(defaultModel))) {
            throw new IllegalArgumentException("defaultModel must reference one of models");
        }
    }

    public static Builder builder(AiModelReference defaultModel) {
        return new Builder(defaultModel);
    }

    public static final class Builder {
        private final AiModelReference defaultModel;
        private List<AiModel> models;
        private AuthCredentialStore credentialStore;
        private OpenAiSubscriptionLoginClientOptions subscriptionLogin;
        private OpenAiSubscriptionLoginHttpTransport subscriptionLoginTransport;
        private OpenAiResponsesProviderOptions responsesProvider;
        private OpenAiTransport responsesTransport;
        private Clock clock;

        private Builder(AiModelReference defaultModel) {
            this.defaultModel = Objects.requireNonNull(defaultModel, "defaultModel");
            this.models = List.of(new AiModel(defaultModel, defaultModel.modelId()));
        }

        public Builder models(List<AiModel> models) {
            this.models = List.copyOf(Objects.requireNonNull(models, "models"));
            return this;
        }

        public Builder credentialStore(AuthCredentialStore credentialStore) {
            this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
            return this;
        }

        public Builder subscriptionLogin(OpenAiSubscriptionLoginClientOptions subscriptionLogin) {
            this.subscriptionLogin = Objects.requireNonNull(subscriptionLogin, "subscriptionLogin");
            return this;
        }

        public Builder subscriptionLoginTransport(OpenAiSubscriptionLoginHttpTransport subscriptionLoginTransport) {
            this.subscriptionLoginTransport = Objects.requireNonNull(subscriptionLoginTransport, "subscriptionLoginTransport");
            return this;
        }

        public Builder responsesProvider(OpenAiResponsesProviderOptions responsesProvider) {
            this.responsesProvider = Objects.requireNonNull(responsesProvider, "responsesProvider");
            return this;
        }

        public Builder responsesTransport(OpenAiTransport responsesTransport) {
            this.responsesTransport = Objects.requireNonNull(responsesTransport, "responsesTransport");
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public OpenAiCodingRuntimeOptions build() {
            List<AiModel> resolvedModels = models == null || models.isEmpty()
                    ? List.of(new AiModel(defaultModel, defaultModel.modelId()))
                    : models;
            return new OpenAiCodingRuntimeOptions(
                    resolvedModels,
                    defaultModel,
                    credentialStore == null ? PersistentAuthCredentialStore.userDefault() : credentialStore,
                    Optional.ofNullable(subscriptionLogin),
                    subscriptionLoginTransport == null
                            ? new DefaultOpenAiSubscriptionLoginHttpTransport()
                            : subscriptionLoginTransport,
                    responsesProvider == null
                            ? OpenAiResponsesProviderOptions.defaults(resolvedModels)
                            : responsesProvider,
                    responsesTransport == null ? new DefaultOpenAiTransport() : responsesTransport,
                    clock == null ? Clock.systemUTC() : clock);
        }
    }
}
