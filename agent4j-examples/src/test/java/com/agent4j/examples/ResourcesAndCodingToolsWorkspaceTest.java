package com.agent4j.examples;

import com.agent4j.coding.resource.ResourceDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ResourcesAndCodingToolsWorkspaceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsOnlyWorkspaceLocalResourcesAndBuildsTheirSystemPrompt() throws Exception {
        Path workspace = temporaryDirectory.resolve("workspace");

        ResourcesAndCodingToolsWorkspace.SampleWorkspace sampleWorkspace =
                ResourcesAndCodingToolsWorkspace.createAndDiscover(workspace);
        ResourceDiscovery discovery = sampleWorkspace.discovery();

        assertThat(workspace.resolve("README.md")).doesNotExist();
        assertThat(Files.readString(sampleWorkspace.path().resolve("README.md"))).contains("Lantern Library");
        assertThat(sampleWorkspace.path().resolve("src/Library.java")).exists();
        assertThat(discovery.settings().textField("exampleMode")).contains("read-only-workspace");
        assertThat(discovery.contextFiles()).extracting(file -> file.path().getFileName().toString())
                .contains("AGENTS.md");
        assertThat(ResourcesAndCodingToolsWorkspace.systemPrompt(discovery))
                .contains("Lantern Library sample workspace")
                .contains("read-only tools")
                .contains("Report paths relative to the workspace")
                .contains("Inspect only files under this sample workspace");
    }
}
