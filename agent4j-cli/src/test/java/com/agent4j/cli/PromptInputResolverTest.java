package com.agent4j.cli;

import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptInputResolverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void expandsTextFilesAndPipedInput() throws Exception {
        Files.writeString(temporaryDirectory.resolve("request.md"), "Review the implementation.");

        List<String> prompt = PromptInputResolver.resolve(
                List.of("Please", "@request.md"), new StringReader("Focus on tests."), true, temporaryDirectory);

        assertThat(prompt).containsExactly("Please", "Review the implementation.", "Focus on tests.");
    }

    @Test
    void rendersDiscoveredTemplatesWithArguments() throws Exception {
        Path workspace = temporaryDirectory.resolve("workspace");
        Files.createDirectories(workspace.resolve(".pi/prompts"));
        Files.writeString(workspace.resolve(".pi/prompts/review.md"), "Review this change: $ARGUMENTS");
        ResourceDiscovery discovery = new ResourceLoader().discover(
                ResourceDiscoveryOptions.enabled(temporaryDirectory.resolve("home"), workspace));

        String prompt = PromptTemplateRenderer.render(discovery, "review", List.of("src/Calculator.java"));

        assertThat(prompt).isEqualTo("Review this change: src/Calculator.java");
    }
}
