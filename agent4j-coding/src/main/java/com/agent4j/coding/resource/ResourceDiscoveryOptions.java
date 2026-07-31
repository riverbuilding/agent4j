package com.agent4j.coding.resource;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ResourceDiscoveryOptions(
        Path homeDir,
        Path cwd,
        boolean contextFilesEnabled,
        boolean promptTemplatesEnabled,
        boolean skillsEnabled,
        boolean themesEnabled,
        boolean packagesEnabled,
        ProjectTrustPolicy projectTrustPolicy,
        List<String> themeSources
) {
    public ResourceDiscoveryOptions(Path homeDir, Path cwd, boolean contextFilesEnabled) {
        this(homeDir, cwd, contextFilesEnabled, true, true);
    }

    public ResourceDiscoveryOptions(
            Path homeDir,
            Path cwd,
            boolean contextFilesEnabled,
            boolean promptTemplatesEnabled
    ) {
        this(homeDir, cwd, contextFilesEnabled, promptTemplatesEnabled, true);
    }

    public ResourceDiscoveryOptions(
            Path homeDir,
            Path cwd,
            boolean contextFilesEnabled,
            boolean promptTemplatesEnabled,
            boolean skillsEnabled
    ) {
        this(
                homeDir,
                cwd,
                contextFilesEnabled,
                promptTemplatesEnabled,
                skillsEnabled,
                true,
                true,
                ProjectTrustPolicy.TRUSTED,
                List.of());
    }

    public ResourceDiscoveryOptions {
        Objects.requireNonNull(homeDir, "homeDir");
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(projectTrustPolicy, "projectTrustPolicy");
        Objects.requireNonNull(themeSources, "themeSources");
        homeDir = homeDir.toAbsolutePath().normalize();
        cwd = cwd.toAbsolutePath().normalize();
        themeSources = List.copyOf(themeSources);
    }

    public static ResourceDiscoveryOptions enabled(Path homeDir, Path cwd) {
        return new ResourceDiscoveryOptions(homeDir, cwd, true, true, true);
    }

    public ResourceDiscoveryOptions withProjectTrustPolicy(ProjectTrustPolicy policy) {
        return new ResourceDiscoveryOptions(
                homeDir,
                cwd,
                contextFilesEnabled,
                promptTemplatesEnabled,
                skillsEnabled,
                themesEnabled,
                packagesEnabled,
                policy,
                themeSources);
    }

    public ResourceDiscoveryOptions withoutThemes() {
        return new ResourceDiscoveryOptions(
                homeDir,
                cwd,
                contextFilesEnabled,
                promptTemplatesEnabled,
                skillsEnabled,
                false,
                packagesEnabled,
                projectTrustPolicy,
                themeSources);
    }

    public ResourceDiscoveryOptions withoutPackages() {
        return new ResourceDiscoveryOptions(
                homeDir,
                cwd,
                contextFilesEnabled,
                promptTemplatesEnabled,
                skillsEnabled,
                themesEnabled,
                false,
                projectTrustPolicy,
                themeSources);
    }

    public ResourceDiscoveryOptions withThemeSources(List<String> sources) {
        return new ResourceDiscoveryOptions(
                homeDir,
                cwd,
                contextFilesEnabled,
                promptTemplatesEnabled,
                skillsEnabled,
                themesEnabled,
                packagesEnabled,
                projectTrustPolicy,
                sources);
    }
}
