package com.agent4j.coding.sdk;

import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.resource.ResourceDiscoveryOptions;
import com.agent4j.coding.resource.ResourceLoader;
import com.agent4j.coding.resource.SystemPromptBuilder;
import com.agent4j.core.tool.ToolRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves resource-derived system context for the workspace recorded by a session. */
public final class RuntimePromptResolver {
    private final ResourceLoader resourceLoader;
    private final SystemPromptBuilder systemPromptBuilder;
    private final ResourceDiscoveryOptions options;
    private final Optional<String> explicitSystemPrompt;
    private final List<String> appendSystemPrompts;

    public RuntimePromptResolver(
            ResourceLoader resourceLoader,
            SystemPromptBuilder systemPromptBuilder,
            ResourceDiscoveryOptions options,
            Optional<String> explicitSystemPrompt,
            List<String> appendSystemPrompts
    ) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
        this.systemPromptBuilder = Objects.requireNonNull(systemPromptBuilder, "systemPromptBuilder");
        this.options = Objects.requireNonNull(options, "options");
        this.explicitSystemPrompt = explicitSystemPrompt == null ? Optional.empty() : explicitSystemPrompt;
        this.appendSystemPrompts = appendSystemPrompts == null ? List.of() : List.copyOf(appendSystemPrompts);
    }

    public ResolvedPromptContext resolve(Path workspace, ToolRegistry toolRegistry) throws IOException {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(toolRegistry, "toolRegistry");
        Path resolvedWorkspace = workspace.toAbsolutePath().normalize();
        ResourceDiscoveryOptions workspaceOptions = new ResourceDiscoveryOptions(
                options.homeDir(), resolvedWorkspace, options.contextFilesEnabled(), options.promptTemplatesEnabled(),
                options.skillsEnabled(), options.themesEnabled(), options.packagesEnabled(), options.projectTrustPolicy(),
                options.themeSources());
        ResourceDiscovery discovery = resourceLoader.discover(workspaceOptions);
        return new ResolvedPromptContext(
                resolvedWorkspace,
                discovery,
                systemPromptBuilder.build(discovery, toolRegistry.specs(), explicitSystemPrompt, appendSystemPrompts));
    }
}
