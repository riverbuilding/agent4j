package com.agent4j.coding.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptBuilderTest {
    @TempDir
    Path tempDir;

    private final ResourceLoader loader = new ResourceLoader();
    private final SystemPromptBuilder builder = new SystemPromptBuilder();

    @Test
    void buildsModelPromptFromDiscoveredResourcesInPiOrder() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/SYSTEM.md"), "global system\n");
        write(home.resolve(".pi/agent/APPEND_SYSTEM.md"), "global append\n");
        write(home.resolve(".pi/agent/AGENTS.md"), "global <context>\n");
        write(cwd.resolve(".pi/SYSTEM.md"), "project system\n");
        write(cwd.resolve(".pi/APPEND_SYSTEM.md"), "project append\n");
        write(cwd.resolve("AGENTS.md"), "project context\n");
        write(cwd.resolve(".pi/skills/review/SKILL.md"), """
                ---
                name: review
                description: Review files <carefully>.
                allowed-tools: read bash
                compatibility: Requires repo checkout.
                ---
                # Review
                """);
        write(cwd.resolve(".pi/skills/manual/SKILL.md"), """
                ---
                name: manual
                description: Manual-only skill.
                disable-model-invocation: true
                ---
                # Manual
                """);

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        String prompt = builder.build(discovery);

        assertThat(prompt).contains("project system\n");
        assertInOrder(
                prompt,
                "project system",
                "global append",
                "project append",
                "<context-files>",
                "global &lt;context&gt;",
                "project context",
                "<skills>",
                "name=\"review\"",
                "Review files &lt;carefully&gt;.");
        assertThat(prompt).doesNotContain("global system");
        assertThat(prompt).doesNotContain("name=\"manual\"");
        assertThat(prompt).doesNotContain("Manual-only skill");
        assertThat(prompt).contains("allowed-tools=\"read bash\"");
        assertThat(prompt).contains("<compatibility>Requires repo checkout.</compatibility>");
    }

    @Test
    void returnsEmptyPromptWhenNoSystemInputsAreDiscovered() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(builder.build(discovery)).isEmpty();
    }

    private static void assertInOrder(String value, String... parts) {
        int previous = -1;
        for (String part : parts) {
            int current = value.indexOf(part);
            assertThat(current).as("expected to find %s", part).isGreaterThanOrEqualTo(0);
            assertThat(current).as("expected %s after previous part", part).isGreaterThan(previous);
            previous = current;
        }
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
