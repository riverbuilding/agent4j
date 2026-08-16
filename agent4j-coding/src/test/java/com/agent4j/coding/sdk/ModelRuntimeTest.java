package com.agent4j.coding.sdk;

import com.agent4j.ai.EnvironmentAiAuthStore;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderApi;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStreamEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesAKnownModelWithoutAnExplicitProvider() {
        ModelRuntime runtime = new ModelRuntime(loginService(Map.of("OPENAI_API_KEY", "test-key")));

        assertThat(runtime.resolve(Optional.empty(), Optional.of("gpt-5")).displayName()).isEqualTo("openai/gpt-5");
    }

    @Test
    void choosesTheOnlyAuthenticatedProviderWhenNoSelectionIsConfigured() {
        ModelRuntime runtime = new ModelRuntime(loginService(Map.of("ANTHROPIC_API_KEY", "test-key")));

        assertThat(runtime.resolve(Optional.empty(), Optional.empty()).displayName())
                .isEqualTo("anthropic/claude-sonnet-4-5");
    }

    @Test
    void requiresASelectionWhenMultipleProvidersAreAuthenticated() {
        ModelRuntime runtime = new ModelRuntime(loginService(Map.of(
                "OPENAI_API_KEY", "openai-key",
                "ANTHROPIC_API_KEY", "anthropic-key")));

        assertThatThrownBy(() -> runtime.resolve(Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiple providers");
    }

    @Test
    void registryIncludesAnExplicitCustomModelForASupportedProvider() {
        ModelRuntime runtime = new ModelRuntime(loginService(Map.of()));

        assertThat(runtime.registry(new com.agent4j.ai.AiModelReference("openai", "company-model"))
                .requireDefault().model().id()).isEqualTo("company-model");
    }

    @Test
    void mergesModelsJsonAndExtensionProvidersIntoTheAllModelRegistry() throws Exception {
        Path modelsFile = temporaryDirectory.resolve("models.json");
        Files.writeString(modelsFile, """
                {
                  "models": [{"provider": "openai", "id": "company-model", "name": "Company Model"}],
                  "defaultModel": "openai/company-model"
                }
                """);
        AiModel extensionModel = new AiModel(new AiModelReference("test", "extension-model"), "Extension Model");
        ModelRuntime runtime = ModelRuntime.builder(loginService(Map.of("OPENAI_API_KEY", "test-key")))
                .modelsJson(modelsFile)
                .extensionProvider(provider("test", extensionModel))
                .build();

        assertThat(runtime.allModels().require(new AiModelReference("openai", "company-model")).model().name())
                .isEqualTo("Company Model");
        assertThat(runtime.allModels().require(extensionModel.reference()).model()).isEqualTo(extensionModel);
        assertThat(runtime.resolve(Optional.empty(), Optional.empty()).displayName()).isEqualTo("openai/company-model");
        assertThat(runtime.availableModels()).extracting(selection -> selection.model().id())
                .contains("gpt-5", "company-model");
    }

    private static LoginService loginService(Map<String, String> environment) {
        return new DefaultLoginService(
                new InMemoryAuthCredentialStore(),
                Clock.systemUTC(),
                new EnvironmentAiAuthStore(environment));
    }

    private static AiProvider provider(String id, AiModel model) {
        return new AiProvider() {
            @Override public String id() { return id; }
            @Override public String name() { return id; }
            @Override public AiProviderApi api() { return AiProviderApi.OPENAI_RESPONSES; }
            @Override public List<AiModel> models() { return List.of(model); }
            @Override public void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
