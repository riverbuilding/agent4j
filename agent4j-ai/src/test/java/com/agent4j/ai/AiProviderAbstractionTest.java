package com.agent4j.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderAbstractionTest {
    @Test
    void fixedClientRegistryAdaptsDirectClientAsItsDefaultProvider() throws Exception {
        AiModel model = new AiModel(new AiModelReference("test", "fixed"), "Fixed model");
        AtomicReference<AiTurnRequest> received = new AtomicReference<>();
        AiProviderRegistry registry = AiProviderRegistry.fixedClient(model, (request, sink) -> received.set(request));
        AiTurnRequest turn = new AiTurnRequest(List.of(AiUserMessage.text("hello")), List.of());

        registry.requireDefault().provider().stream(new AiProviderRequest(
                model, turn, AiProviderContext.empty(), AiStreamOptions.defaults()), event -> { });

        assertThat(registry.requireDefault().model()).isEqualTo(model);
        assertThat(received.get()).isSameAs(turn);
    }

    @Test
    void modelsCarryPiStyleProviderMetadataWithStableDefaults() {
        AiModel model = new AiModel(new AiModelReference("openai", "gpt-5.6-sol"), "GPT 5.6 Sol");

        assertThat(model.providerId()).isEqualTo("openai");
        assertThat(model.id()).isEqualTo("gpt-5.6-sol");
        assertThat(model.input()).containsExactly(AiInputType.TEXT);
        assertThat(model.contextWindow()).isEqualTo(128000);
        assertThat(model.maxTokens()).isEqualTo(16384);
        assertThat(model.cost()).isEqualTo(AiCost.zero());
        assertThat(model.compat()).isEqualTo(AiModelCompat.defaults());
        assertThat(model.features()).isEqualTo(AiModelFeatures.defaults());
    }

    @Test
    void modelMetadataDefensivelyCopiesCollections() {
        AiModel model = new AiModel(
                new AiModelReference("anthropic", "claude-sonnet"),
                "Claude Sonnet",
                Optional.of(AiProviderApi.ANTHROPIC_MESSAGES),
                Optional.empty(),
                true,
                Map.of(AiThinkingLevel.HIGH, "high"),
                EnumSet.of(AiThinkingLevel.MINIMAL),
                EnumSet.of(AiInputType.TEXT, AiInputType.IMAGE),
                200000,
                32000,
                new AiCost(new AiTokenCost(3, 15, 0.3, 3.75), List.of(new AiCostTier(200000, AiTokenCost.zero()))),
                AiModelCompat.defaults(),
                null);

        assertThatThrownBy(() -> model.input().add(AiInputType.TEXT))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(model.thinkingLevelMap()).containsEntry(AiThinkingLevel.HIGH, "high");
        assertThat(model.unsupportedThinkingLevels()).containsExactly(AiThinkingLevel.MINIMAL);
        assertThat(model.features().imageInput()).isTrue();
        assertThat(model.features().reasoning()).isTrue();
    }

    @Test
    void providerFeaturesHaveStableDefaults() {
        assertThat(AiProviderFeatures.defaults().streaming()).isTrue();
        assertThat(AiProviderFeatures.defaults().toolCalling()).isTrue();
        assertThat(AiProviderFeatures.defaults().parallelToolCalls()).isTrue();
        assertThat(AiProviderFeatures.withoutParallelToolCalls().parallelToolCalls()).isFalse();
    }

    @Test
    void providerRequestAppliesAuthBaseUrlToEffectiveRequestModel() {
        AiModel catalogModel = new AiModel(new AiModelReference("openai", "gpt-5"), "GPT-5")
                .withBaseUrl("https://catalog.test/v1");
        AiResolvedAuth auth = new AiResolvedAuth(
                Optional.empty(),
                Map.of(),
                Optional.of("https://auth.test/v1"),
                Optional.of("test"),
                Map.of());

        AiProviderRequest request = new AiProviderRequest(
                catalogModel,
                new AiTurnRequest(List.of(AiUserMessage.text("hello")), List.of()),
                new AiProviderContext(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        auth,
                        Map.of(),
                        Map.of()),
                AiStreamOptions.defaults());

        assertThat(request.model().baseUrl()).contains("https://auth.test/v1");
        assertThat(catalogModel.baseUrl()).contains("https://catalog.test/v1");
    }

    @Test
    void endpointResolverUsesModelBaseUrlWithProviderPathSuffix() {
        AiModel model = new AiModel(new AiModelReference("anthropic", "claude"), "Claude")
                .withBaseUrl("https://anthropic.test/v1/");

        assertThat(AiEndpointResolver.endpoint(
                model,
                java.net.URI.create("https://api.anthropic.com/v1/messages"),
                "/messages"))
                .hasToString("https://anthropic.test/v1/messages");
        assertThat(AiEndpointResolver.endpoint(
                model.withBaseUrl("https://anthropic.test/v1/messages"),
                java.net.URI.create("https://api.anthropic.com/v1/messages"),
                "/messages"))
                .hasToString("https://anthropic.test/v1/messages");
    }

    @Test
    void streamOptionsValidateTimeoutAndRetryShape() {
        assertThat(AiStreamOptions.defaults().signal().aborted()).isFalse();
        assertThat(AiStreamOptions.defaults().maxRetries()).isZero();
        assertThat(AiStreamOptions.defaults().generation()).isEqualTo(AiGenerationOptions.defaults());

        assertThatThrownBy(() -> new AiStreamOptions(
                AiAbortSignal.none(),
                Optional.of(Duration.ZERO),
                0,
                Map.of(),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
        assertThatThrownBy(() -> new AiStreamOptions(
                AiAbortSignal.none(),
                Optional.empty(),
                -1,
                Map.of(),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRetries");
    }

    @Test
    void retryClassifierPinsTransientProviderFailures() {
        assertThat(new AiProviderHttpException("openai", 429, "rate limit").retryable()).isTrue();
        assertThat(new AiProviderHttpException("openai", 503, "overloaded").retryable()).isTrue();
        assertThat(new AiProviderHttpException("openai", 400, "bad request").retryable()).isFalse();
        assertThat(AiRetryClassifier.isRetryable(new IOException("network reset"))).isTrue();
        assertThat(AiRetryClassifier.isRetryable(new IllegalArgumentException("invalid input"))).isFalse();
    }

    @Test
    void generationOptionsValidateCommonProviderRequestKnobs() {
        AiGenerationOptions options = new AiGenerationOptions(
                Optional.of(1024),
                Optional.of(0.7),
                Optional.of(0.9),
                Optional.of(40),
                Optional.of(" auto "),
                false,
                Map.of("session", "abc"));

        assertThat(options.maxOutputTokens()).contains(1024);
        assertThat(options.toolChoice()).contains("auto");
        assertThat(options.parallelToolCalls()).isFalse();
        assertThat(options.metadata()).containsEntry("session", "abc");
        assertThatThrownBy(() -> new AiGenerationOptions(
                Optional.of(0),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true,
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxOutputTokens");
        assertThatThrownBy(() -> new AiGenerationOptions(
                Optional.empty(),
                Optional.of(3.0),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true,
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature");
        assertThatThrownBy(() -> new AiGenerationOptions(
                Optional.empty(),
                Optional.empty(),
                Optional.of(1.5),
                Optional.empty(),
                Optional.empty(),
                true,
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topP");
    }

    @Test
    void providerStreamsNormalizedEventsFromProviderRequest() throws Exception {
        AiModel model = new AiModel(new AiModelReference("fake", "model"), "Fake Model");
        AiProvider provider = new FakeProvider(List.of(model));
        AiProviderRequest request = new AiProviderRequest(
                model,
                new AiTurnRequest(List.of(AiUserMessage.text("hello")), List.of()),
                AiProviderContext.empty(),
                AiStreamOptions.defaults());
        List<AiStreamEvent> events = new ArrayList<>();

        provider.stream(request, events::add);

        assertThat(provider.model("model")).contains(model);
        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsExactly("MessageStarted", "TextStarted", "TextDelta", "TextEnded", "MessageCompleted");
    }

    @Test
    void providerRegistryResolvesDefaultAndExplicitProviderModels() {
        AiModel first = new AiModel(new AiModelReference("fake", "first"), "First");
        AiModel second = new AiModel(new AiModelReference("other", "second"), "Second");
        AiProviderRegistry registry = AiProviderRegistry.builder()
                .add(new FakeProvider(List.of(first)))
                .add(new OtherFakeProvider(List.of(second)))
                .defaultModel(second.reference())
                .build();

        assertThat(registry.requireDefault().model()).isEqualTo(second);
        assertThat(registry.require(new AiModelReference("fake", "first")).model()).isEqualTo(first);
        assertThat(registry.provider("other")).isPresent();
        assertThatThrownBy(() -> AiProviderRegistry.builder()
                .add(new FakeProvider(List.of(first)))
                .add(new FakeProvider(List.of(first)))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate provider");
        assertThatThrownBy(() -> registry.require(new AiModelReference("missing", "model")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown provider/model");
    }

    @Test
    void authStoresResolveInMemoryAndEnvironmentAuth() {
        AiResolvedAuth configured = new AiResolvedAuth(
                Optional.of("sk-test"),
                Map.of("X-Test", "yes"),
                Optional.of("https://example.test"),
                Optional.of("test"),
                Map.of());
        AiAuthStore memory = InMemoryAiAuthStore.builder()
                .put("openai", configured)
                .build();
        AiAuthStore environment = new EnvironmentAiAuthStore(Map.of(
                "ANTHROPIC_API_KEY", "sk-ant-test",
                "ANTHROPIC_BASE_URL", "https://anthropic.test"));

        assertThat(memory.resolve("openai")).contains(configured);
        assertThat(memory.resolve("anthropic")).isEmpty();
        assertThat(environment.resolve("anthropic")).hasValueSatisfying(auth -> {
            assertThat(auth.apiKey()).contains("sk-ant-test");
            assertThat(auth.baseUrl()).contains("https://anthropic.test");
            assertThat(auth.source()).contains("environment");
        });
        assertThat(environment.resolve("openai")).isEmpty();
    }

    @Test
    void resolvedAuthCarriesExplicitModesAndBearerTokens() {
        Instant expiresAt = Instant.parse("2026-08-04T12:00:00Z");
        AiResolvedAuth subscription = AiResolvedAuth.chatGptSubscription(
                "access-token",
                Optional.of("https://codex.openai.com/api"),
                Optional.of("login"),
                Optional.of(expiresAt),
                Map.of("plan", "plus"));

        assertThat(subscription.mode()).isEqualTo(AiAuthMode.CHATGPT_SUBSCRIPTION);
        assertThat(subscription.apiKey()).isEmpty();
        assertThat(subscription.accessToken()).contains("access-token");
        assertThat(subscription.authorizationBearerToken()).contains("access-token");
        assertThat(subscription.expiresAt()).contains(expiresAt);
        assertThat(subscription.metadata()).containsEntry("plan", "plus");
        assertThat(subscription.hasCredentials()).isTrue();
        assertThat(new AiResolvedAuth(
                Optional.empty(),
                Map.of("Authorization", "Bearer custom"),
                Optional.empty(),
                Optional.of("test"),
                Map.of()).mode()).isEqualTo(AiAuthMode.CUSTOM_HEADERS);
    }

    private record FakeProvider(List<AiModel> models) implements AiProvider {
        private FakeProvider {
            models = List.copyOf(models);
        }

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public String name() {
            return "Fake";
        }

        @Override
        public AiProviderApi api() {
            return AiProviderApi.CUSTOM;
        }

        @Override
        public void stream(AiProviderRequest request, java.util.function.Consumer<AiStreamEvent> sink) {
            request.options().signal().throwIfAborted();
            sink.accept(new AiStreamEvent.MessageStarted("assistant-1"));
            sink.accept(new AiStreamEvent.TextStarted("assistant-1", 0));
            sink.accept(new AiStreamEvent.TextDelta("assistant-1", 0, "ok"));
            sink.accept(new AiStreamEvent.TextEnded("assistant-1", 0));
            sink.accept(new AiStreamEvent.MessageCompleted(
                    "assistant-1",
                    new AiAssistantMessage(
                            List.of(new AiTextContent("ok")),
                            AiStopReason.STOP,
                            AiUsage.zero())));
        }
    }

    private record OtherFakeProvider(List<AiModel> models) implements AiProvider {
        private OtherFakeProvider {
            models = List.copyOf(models);
        }

        @Override
        public String id() {
            return "other";
        }

        @Override
        public String name() {
            return "Other Fake";
        }

        @Override
        public AiProviderApi api() {
            return AiProviderApi.CUSTOM;
        }

        @Override
        public void stream(AiProviderRequest request, java.util.function.Consumer<AiStreamEvent> sink) {
            throw new UnsupportedOperationException();
        }
    }
}
