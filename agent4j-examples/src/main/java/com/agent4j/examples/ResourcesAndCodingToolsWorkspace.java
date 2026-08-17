package com.agent4j.examples;

import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.resource.SystemPromptBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ResourcesAndCodingToolsWorkspace {
    private ResourcesAndCodingToolsWorkspace() {
    }

    static SampleWorkspace createAndDiscover(Path workspaceRoot) throws IOException {
        Files.createDirectories(workspaceRoot);
        Path workspace = Files.createTempDirectory(workspaceRoot, "06-resources-and-coding-tools-");
        Path homeDirectory = workspace.resolve(".example-home");
        write(workspace.resolve("README.md"), "# Sample Library\n\nThe project name is Lantern Library.\n");
        write(workspace.resolve("src/Library.java"), "package sample;\n\nfinal class Library {\n}\n");
        write(workspace.resolve("AGENTS.md"), "Inspect only files under this sample workspace.\n");
        write(workspace.resolve(".pi/SYSTEM.md"), "You are documenting the Lantern Library sample workspace.\n");
        write(workspace.resolve(".pi/APPEND_SYSTEM.md"), "Use only the provided read-only tools.\n");
        write(workspace.resolve(".pi/settings.json"), """
                {
                  "retry": { "maxRetries": 0 },
                  "httpIdleTimeoutMs": 30000,
                  "exampleMode": "read-only-workspace"
                }
                """);
        write(homeDirectory.resolve(".pi/agent/APPEND_SYSTEM.md"), "Report paths relative to the workspace.\n");

        return new SampleWorkspace(
                workspace,
                new ResourceLoader().discover(ResourceDiscoveryOptions.enabled(homeDirectory, workspace)));
    }

    static String systemPrompt(ResourceDiscovery discovery) {
        return new SystemPromptBuilder().build(discovery);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    record SampleWorkspace(Path path, ResourceDiscovery discovery) {
    }
}
