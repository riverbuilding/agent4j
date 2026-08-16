package com.agent4j.cli;

import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.sdk.InMemoryAuthCredentialStore;
import com.agent4j.coding.tool.CodingTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCliRuntimeFactoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesProjectSettingsAndBuildsSdkOwnedOpenAiRuntime() throws Exception {
        Path home = temporaryDirectory.resolve("home");
        Path workspace = temporaryDirectory.resolve("workspace");
        Files.createDirectories(workspace.resolve(".pi"));
        Files.writeString(workspace.resolve(".pi/settings.json"), """
                {
                  "defaultProvider": "openai",
                  "defaultModel": "gpt-from-settings"
                }
                """);
        InMemoryAuthCredentialStore persistentStore = new InMemoryAuthCredentialStore();
        DefaultCliRuntimeFactory factory = new DefaultCliRuntimeFactory(
                new ResourceLoader(),
                persistentStore,
                CodingTools.localDefaults().registry(),
                Clock.systemUTC());

        CliRuntime runtime = factory.create(new CliRuntimeRequest(
                workspace,
                home,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));

        assertThat(runtime.defaultModel().displayName()).isEqualTo("openai/gpt-from-settings");
        assertThat(runtime.resourceDiscovery().directories().projectAgentDir()).isEqualTo(workspace.resolve(".pi"));
        assertThat(runtime.runtime()).isNotNull();
        assertThat(persistentStore.find("openai")).isEmpty();
    }

    @Test
    void commandLineApiKeyUsesEphemeralRuntimeCredentials() throws Exception {
        InMemoryAuthCredentialStore persistentStore = new InMemoryAuthCredentialStore();
        DefaultCliRuntimeFactory factory = new DefaultCliRuntimeFactory(
                new ResourceLoader(),
                persistentStore,
                CodingTools.localDefaults().registry(),
                Clock.systemUTC());

        CliRuntime runtime = factory.create(new CliRuntimeRequest(
                temporaryDirectory.resolve("workspace"),
                temporaryDirectory.resolve("home"),
                Optional.of("openai"),
                Optional.of("gpt-runtime"),
                Optional.of("sk-runtime-only")));

        assertThat(runtime.defaultModel().displayName()).isEqualTo("openai/gpt-runtime");
        assertThat(persistentStore.find("openai")).isEmpty();
    }

    @Test
    void loadsTheProjectModelsJsonBeforeSelectingTheDefaultModel() throws Exception {
        Path workspace = temporaryDirectory.resolve("workspace");
        Files.createDirectories(workspace.resolve(".pi"));
        Files.writeString(workspace.resolve(".pi/models.json"), """
                {
                  "models": [{"provider": "openai", "id": "company-model", "name": "Company Model"}],
                  "defaultModel": "openai/company-model"
                }
                """);
        DefaultCliRuntimeFactory factory = new DefaultCliRuntimeFactory(
                new ResourceLoader(),
                new InMemoryAuthCredentialStore(),
                CodingTools.localDefaults().registry(),
                Clock.systemUTC());

        CliRuntime runtime = factory.create(new CliRuntimeRequest(
                workspace, temporaryDirectory.resolve("home"), Optional.empty(), Optional.empty(), Optional.empty()));

        assertThat(runtime.defaultModel().displayName()).isEqualTo("openai/company-model");
    }

    @Test
    void buildsAnAnthropicRuntimeFromTheBuiltInCatalog() throws Exception {
        DefaultCliRuntimeFactory factory = new DefaultCliRuntimeFactory(
                new ResourceLoader(),
                new InMemoryAuthCredentialStore(),
                CodingTools.localDefaults().registry(),
                Clock.systemUTC());

        CliRuntime runtime = factory.create(new CliRuntimeRequest(
                temporaryDirectory.resolve("workspace"),
                temporaryDirectory.resolve("home"),
                Optional.of("anthropic"),
                Optional.of("claude"),
                Optional.empty()));

        assertThat(runtime.defaultModel().displayName()).isEqualTo("anthropic/claude");
    }
}
