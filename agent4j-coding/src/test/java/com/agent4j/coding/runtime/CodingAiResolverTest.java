package com.agent4j.coding.runtime;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderApi;
import com.agent4j.ai.AiProviderRegistry;
import com.agent4j.ai.AiResolvedAuth;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiProvider;
import com.agent4j.coding.resource.AgentSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class CodingAiResolverTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void resolvesSelectionFromDefaultProviderAndDefaultModelSettings() throws Exception {
        AiModel openai = new AiModel(new AiModelReference("openai", "gpt-5"), "GPT-5");
        AiModel anthropic = new AiModel(new AiModelReference("anthropic", "claude-sonnet"), "Claude Sonnet");
        CodingAiResolver resolver = new CodingAiResolver(registry(openai, anthropic));
        AgentSettings settings = settings("""
                {
                  "defaultProvider": "anthropic",
                  "defaultModel": "claude-sonnet"
                }
                """);

        assertThat(resolver.resolveSelection(settings).provider().id()).isEqualTo("anthropic");
        assertThat(resolver.resolveSelection(settings).model()).isEqualTo(anthropic);
    }

    @Test
    void resolvesSelectionFromProviderSpecificDefaultModelSettings() throws Exception {
        AiModel openai = new AiModel(new AiModelReference("openai", "gpt-5"), "GPT-5");
        AiModel anthropic = new AiModel(new AiModelReference("anthropic", "claude-sonnet"), "Claude Sonnet");
        CodingAiResolver resolver = new CodingAiResolver(registry(openai, anthropic));
        AgentSettings settings = settings("""
                {
                  "defaultProvider": "openai",
                  "models": {
                    "openai": {
                      "default": "gpt-5"
                    }
                  }
                }
                """);

        assertThat(resolver.resolveSelection(settings).provider().id()).isEqualTo("openai");
        assertThat(resolver.resolveSelection(settings).model()).isEqualTo(openai);
    }

    @Test
    void resolvesProviderAuthFromSettings() throws Exception {
        AiModel openai = new AiModel(new AiModelReference("openai", "gpt-5"), "GPT-5");
        CodingAiResolver resolver = new CodingAiResolver(AiProviderRegistry.builder()
                .add(new StaticProvider("openai", List.of(openai)))
                .build());
        AgentSettings settings = settings("""
                {
                  "providers": {
                    "openai": {
                      "apiKey": "sk-test",
                      "baseUrl": "https://api.test/v1",
                      "headers": {
                        "OpenAI-Beta": "responses=v1"
                      }
                    }
                  }
                }
                """);

        AiResolvedAuth auth = resolver.resolveAuth(settings, "openai");

        assertThat(auth.apiKey()).contains("sk-test");
        assertThat(auth.baseUrl()).contains("https://api.test/v1");
        assertThat(auth.headers()).containsEntry("OpenAI-Beta", "responses=v1");
        assertThat(auth.source()).contains("settings");
        assertThat(resolver.resolveAuth(settings, "anthropic")).isEqualTo(AiResolvedAuth.none());
    }

    private static AiProviderRegistry registry(AiModel openai, AiModel anthropic) {
        return AiProviderRegistry.builder()
                .add(new StaticProvider("openai", List.of(openai)))
                .add(new StaticProvider("anthropic", List.of(anthropic)))
                .defaultModel(openai.reference())
                .build();
    }

    private static AgentSettings settings(String json) throws Exception {
        return new AgentSettings((com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(json));
    }

    private record StaticProvider(String id, List<AiModel> models) implements AiProvider {
        private StaticProvider {
            models = List.copyOf(models);
        }

        @Override
        public String name() {
            return id;
        }

        @Override
        public AiProviderApi api() {
            return AiProviderApi.CUSTOM;
        }

        @Override
        public void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) {
            throw new UnsupportedOperationException();
        }
    }
}
