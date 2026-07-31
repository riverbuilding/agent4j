package com.agent4j.coding.resource;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ResourceDiscovery(
        AgentResourceDirectories directories,
        List<SettingsFile> settingsFiles,
        AgentSettings settings,
        List<ResourceFile> contextFiles,
        Optional<ResourceFile> systemPrompt,
        List<ResourceFile> appendSystemFiles,
        List<PromptTemplate> promptTemplates,
        List<Skill> skills,
        List<Theme> themes,
        List<ResourceDiagnostic> diagnostics
) {
    public ResourceDiscovery {
        Objects.requireNonNull(directories, "directories");
        Objects.requireNonNull(settingsFiles, "settingsFiles");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(contextFiles, "contextFiles");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(appendSystemFiles, "appendSystemFiles");
        Objects.requireNonNull(promptTemplates, "promptTemplates");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(themes, "themes");
        Objects.requireNonNull(diagnostics, "diagnostics");
        settingsFiles = List.copyOf(settingsFiles);
        contextFiles = List.copyOf(contextFiles);
        appendSystemFiles = List.copyOf(appendSystemFiles);
        promptTemplates = List.copyOf(promptTemplates);
        skills = List.copyOf(skills);
        themes = List.copyOf(themes);
        diagnostics = List.copyOf(diagnostics);
    }
}
