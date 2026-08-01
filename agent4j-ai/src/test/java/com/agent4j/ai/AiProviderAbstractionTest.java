package com.agent4j.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderAbstractionTest {
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
                AiModelCompat.defaults());

        assertThatThrownBy(() -> model.input().add(AiInputType.TEXT))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(model.thinkingLevelMap()).containsEntry(AiThinkingLevel.HIGH, "high");
        assertThat(model.unsupportedThinkingLevels()).containsExactly(AiThinkingLevel.MINIMAL);
    }

    @Test
    void streamOptionsValidateTimeoutAndRetryShape() {
        assertThat(AiStreamOptions.defaults().signal().aborted()).isFalse();

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
