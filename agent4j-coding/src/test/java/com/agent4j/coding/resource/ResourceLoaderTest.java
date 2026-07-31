package com.agent4j.coding.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceLoaderTest {
    @TempDir
    Path tempDir;

    private final ResourceLoader loader = new ResourceLoader();

    @Test
    void resolvesPiStyleGlobalAndProjectAgentDirectories() {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");

        AgentResourceDirectories directories = loader.directories(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(directories.globalAgentDir()).isEqualTo(home.resolve(".pi/agent").toAbsolutePath().normalize());
        assertThat(directories.projectAgentDir()).isEqualTo(cwd.resolve(".pi").toAbsolutePath().normalize());
    }

    @Test
    void loadsContextFilesFromGlobalThenParentsThenCurrentDirectory() throws Exception {
        Path home = tempDir.resolve("home");
        Path repo = tempDir.resolve("repo");
        Path packageDir = repo.resolve("packages/app");
        write(home.resolve(".pi/agent/AGENTS.md"), "global\n");
        write(repo.resolve("AGENTS.md"), "repo\n");
        write(repo.resolve("packages/CLAUDE.md"), "packages\n");
        write(packageDir.resolve("AGENTS.md"), "app\n");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, packageDir));

        assertThat(discovery.contextFiles()).extracting(ResourceFile::content)
                .containsExactly("global\n", "repo\n", "packages\n", "app\n");
        assertThat(discovery.contextFiles()).extracting(ResourceFile::scope)
                .containsExactly(ResourceScope.GLOBAL, ResourceScope.PARENT, ResourceScope.PARENT, ResourceScope.CURRENT);
        assertThat(discovery.contextFiles()).extracting(ResourceFile::type)
                .containsExactly(
                        ResourceFileType.AGENTS,
                        ResourceFileType.AGENTS,
                        ResourceFileType.CLAUDE,
                        ResourceFileType.AGENTS);
    }

    @Test
    void prefersAgentsOverClaudeWithinTheSameDirectory() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(cwd.resolve("AGENTS.md"), "agents\n");
        write(cwd.resolve("CLAUDE.md"), "claude\n");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(discovery.contextFiles()).extracting(ResourceFile::content)
                .containsExactly("agents\n");
        assertThat(discovery.contextFiles().getFirst().type()).isEqualTo(ResourceFileType.AGENTS);
    }

    @Test
    void canDisableContextFileDiscoveryWithoutDisablingSystemPromptFiles() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/AGENTS.md"), "global\n");
        write(cwd.resolve("AGENTS.md"), "project\n");
        write(cwd.resolve(".pi/SYSTEM.md"), "system\n");

        ResourceDiscovery discovery = loader.discover(new ResourceDiscoveryOptions(home, cwd, false));

        assertThat(discovery.contextFiles()).isEmpty();
        assertThat(discovery.systemPrompt()).hasValueSatisfying(system ->
                assertThat(system.content()).isEqualTo("system\n"));
    }

    @Test
    void projectSystemPromptOverridesGlobalAndAppendSystemFilesRemainOrdered() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/SYSTEM.md"), "global system\n");
        write(home.resolve(".pi/agent/APPEND_SYSTEM.md"), "global append\n");
        write(cwd.resolve(".pi/SYSTEM.md"), "project system\n");
        write(cwd.resolve(".pi/APPEND_SYSTEM.md"), "project append\n");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(discovery.systemPrompt()).hasValueSatisfying(system -> {
            assertThat(system.scope()).isEqualTo(ResourceScope.PROJECT);
            assertThat(system.content()).isEqualTo("project system\n");
        });
        assertThat(discovery.appendSystemFiles()).extracting(ResourceFile::content)
                .containsExactly("global append\n", "project append\n");
    }

    @Test
    void loadsGlobalAndProjectSettingsWithNestedProjectOverrides() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/settings.json"), """
                {
                  "defaultProvider": "anthropic",
                  "theme": "dark",
                  "compaction": {
                    "enabled": true,
                    "reserveTokens": 16384
                  },
                  "retry": {
                    "enabled": true,
                    "maxRetries": 3
                  },
                  "skills": ["global-skill"],
                  "futureField": {
                    "kept": true
                  }
                }
                """);
        write(cwd.resolve(".pi/settings.json"), """
                {
                  "theme": "light",
                  "compaction": {
                    "reserveTokens": 8192
                  },
                  "skills": ["project-skill"],
                  "projectOnly": "yes"
                }
                """);

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(discovery.settingsFiles()).extracting(SettingsFile::scope)
                .containsExactly(ResourceScope.GLOBAL, ResourceScope.PROJECT);
        assertThat(discovery.settings().textField("defaultProvider")).contains("anthropic");
        assertThat(discovery.settings().textField("theme")).contains("light");
        assertThat(discovery.settings().values().at("/compaction/enabled").asBoolean()).isTrue();
        assertThat(discovery.settings().values().at("/compaction/reserveTokens").asInt()).isEqualTo(8192);
        assertThat(discovery.settings().values().at("/retry/maxRetries").asInt()).isEqualTo(3);
        assertThat(discovery.settings().textArrayField("skills")).containsExactly("project-skill");
        assertThat(discovery.settings().values().at("/futureField/kept").asBoolean()).isTrue();
        assertThat(discovery.settings().textField("projectOnly")).contains("yes");
    }

    @Test
    void exposesTypedSettingsAccessors() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/settings.json"), """
                {
                  "enableSkillCommands": false,
                  "httpIdleTimeoutMs": 300000,
                  "prompts": ["prompts/*.md", "!prompts/private.md"]
                }
                """);

        AgentSettings settings = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd)).settings();

        assertThat(settings.booleanField("enableSkillCommands")).contains(false);
        assertThat(settings.intField("httpIdleTimeoutMs")).contains(300000);
        assertThat(settings.textArrayField("prompts")).containsExactly("prompts/*.md", "!prompts/private.md");
    }

    @Test
    void rejectsSettingsFilesThatAreNotJsonObjects() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/settings.json"), "[\"not\", \"object\"]");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        loader.discover(ResourceDiscoveryOptions.enabled(home, cwd)))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("settings file must contain a JSON object");
    }

    @Test
    void loadsPromptTemplatesFromDefaultGlobalAndProjectPromptDirectories() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/prompts/review.md"), """
                ---
                description: Review staged changes
                argument-hint: "<scope>"
                ---
                Review $ARGUMENTS
                """);
        write(cwd.resolve(".pi/prompts/component.md"), "Create component $1\n");
        write(cwd.resolve(".pi/prompts/notes.txt"), "ignored\n");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(discovery.promptTemplates()).extracting(PromptTemplate::name)
                .containsExactly("review", "component");
        PromptTemplate review = discovery.promptTemplates().getFirst();
        assertThat(review.scope()).isEqualTo(ResourceScope.GLOBAL);
        assertThat(review.description()).contains("Review staged changes");
        assertThat(review.argumentHint()).contains("<scope>");
        assertThat(review.content()).isEqualTo("Review $ARGUMENTS\n");
        PromptTemplate component = discovery.promptTemplates().get(1);
        assertThat(component.scope()).isEqualTo(ResourceScope.PROJECT);
        assertThat(component.description()).contains("Create component $1");
    }

    @Test
    void projectPromptTemplateWithSameNameOverridesGlobalTemplate() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/prompts/review.md"), "global review\n");
        write(cwd.resolve(".pi/prompts/review.md"), "project review\n");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(discovery.promptTemplates()).singleElement().satisfies(template -> {
            assertThat(template.name()).isEqualTo("review");
            assertThat(template.scope()).isEqualTo(ResourceScope.PROJECT);
            assertThat(template.content()).isEqualTo("project review\n");
        });
    }

    @Test
    void loadsPromptTemplatesFromSettingsPathsRelativeToTheirSettingsFiles() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/settings.json"), """
                {
                  "prompts": ["prompt-sources/*.md", "!prompt-sources/private.md"]
                }
                """);
        write(home.resolve(".pi/agent/prompt-sources/global.md"), "global prompt\n");
        write(home.resolve(".pi/agent/prompt-sources/private.md"), "private prompt\n");
        write(cwd.resolve(".pi/settings.json"), """
                {
                  "prompts": ["templates"]
                }
                """);
        write(cwd.resolve(".pi/templates/project.md"), "project prompt\n");
        write(cwd.resolve(".pi/templates/nested/ignored.md"), "nested ignored\n");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(discovery.promptTemplates()).extracting(PromptTemplate::name)
                .containsExactly("global", "project");
        assertThat(discovery.promptTemplates()).extracting(PromptTemplate::content)
                .containsExactly("global prompt\n", "project prompt\n");
    }

    @Test
    void canDisablePromptTemplateDiscovery() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/prompts/review.md"), "global review\n");
        write(cwd.resolve(".pi/prompts/project.md"), "project prompt\n");
        write(cwd.resolve("AGENTS.md"), "context\n");

        ResourceDiscovery discovery = loader.discover(new ResourceDiscoveryOptions(home, cwd, true, false));

        assertThat(discovery.promptTemplates()).isEmpty();
        assertThat(discovery.contextFiles()).singleElement().satisfies(context ->
                assertThat(context.content()).isEqualTo("context\n"));
    }

    @Test
    void loadsSkillsFromPiAndAgentsDirectories() throws Exception {
        Path home = tempDir.resolve("home");
        Path repo = tempDir.resolve("repo");
        Path cwd = repo.resolve("packages/app");
        writeSkill(home.resolve(".pi/agent/skills/global-root.md"), "global-root", "Global root skill");
        writeSkill(home.resolve(".pi/agent/skills/global-dir/SKILL.md"), "global-dir", "Global directory skill");
        writeSkill(home.resolve(".agents/skills/global-agent-root.md"), "ignored-root", "Ignored root markdown");
        writeSkill(home.resolve(".agents/skills/global-agent-dir/SKILL.md"), "global-agent-dir", "Global agent skill");
        writeSkill(cwd.resolve(".pi/skills/project-root.md"), "project-root", "Project root skill");
        writeSkill(repo.resolve(".agents/skills/repo-agent/SKILL.md"), "repo-agent", "Repo agent skill");
        writeSkill(cwd.resolve(".agents/skills/current-agent/SKILL.md"), "current-agent", "Current agent skill");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(discovery.skills()).extracting(Skill::name)
                .containsExactly(
                        "project-root",
                        "repo-agent",
                        "current-agent",
                        "global-root",
                        "global-dir",
                        "global-agent-dir");
        assertThat(discovery.skills()).extracting(Skill::scope)
                .containsExactly(
                        ResourceScope.PROJECT,
                        ResourceScope.PROJECT,
                        ResourceScope.PROJECT,
                        ResourceScope.GLOBAL,
                        ResourceScope.GLOBAL,
                        ResourceScope.GLOBAL);
        assertThat(discovery.skills()).noneMatch(skill -> skill.name().equals("ignored-root"));
    }

    @Test
    void parsesSkillFrontmatterMetadata() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/skills/pdf/SKILL.md"), """
                ---
                name: pdf-tools
                description: Work with PDF files.
                license: MIT
                compatibility: Requires poppler.
                allowed-tools: read bash
                disable-model-invocation: true
                ---
                # PDF Tools
                """);

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(discovery.skills()).singleElement().satisfies(skill -> {
            assertThat(skill.name()).isEqualTo("pdf-tools");
            assertThat(skill.description()).isEqualTo("Work with PDF files.");
            assertThat(skill.license()).contains("MIT");
            assertThat(skill.compatibility()).contains("Requires poppler.");
            assertThat(skill.allowedTools()).containsExactly("read", "bash");
            assertThat(skill.disableModelInvocation()).isTrue();
            assertThat(skill.content()).isEqualTo("# PDF Tools\n");
        });
    }

    @Test
    void projectSkillKeepsFirstNameCollisionAndReportsDiagnostic() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        writeSkill(cwd.resolve(".pi/skills/review/SKILL.md"), "review", "Project review");
        writeSkill(home.resolve(".pi/agent/skills/review/SKILL.md"), "review", "Global review");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(discovery.skills()).singleElement().satisfies(skill -> {
            assertThat(skill.name()).isEqualTo("review");
            assertThat(skill.description()).isEqualTo("Project review");
            assertThat(skill.scope()).isEqualTo(ResourceScope.PROJECT);
        });
        assertThat(discovery.diagnostics()).singleElement().satisfies(diagnostic ->
                assertThat(diagnostic.message()).isEqualTo("duplicate skill ignored: review"));
    }

    @Test
    void loadsSkillsFromSettingsPathsRelativeToTheirSettingsFiles() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/settings.json"), """
                {
                  "skills": ["shared-skills"]
                }
                """);
        writeSkill(home.resolve(".pi/agent/shared-skills/global/SKILL.md"), "global-settings-skill", "Global settings skill");
        write(home.resolve(".pi/agent/shared-skills/root.md"), """
                ---
                name: global-root-settings-skill
                description: Global root settings skill
                ---
                root body
                """);
        write(cwd.resolve(".pi/settings.json"), """
                {
                  "skills": ["project-skill.md"]
                }
                """);
        writeSkill(cwd.resolve(".pi/project-skill.md"), "project-settings-skill", "Project settings skill");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(discovery.skills()).extracting(Skill::name)
                .containsExactly(
                        "project-settings-skill",
                        "global-root-settings-skill",
                        "global-settings-skill");
    }

    @Test
    void canDisableSkillDiscovery() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        writeSkill(home.resolve(".pi/agent/skills/review/SKILL.md"), "review", "Review skill");
        write(cwd.resolve(".pi/prompts/review.md"), "review prompt\n");

        ResourceDiscovery discovery = loader.discover(new ResourceDiscoveryOptions(home, cwd, true, true, false));

        assertThat(discovery.skills()).isEmpty();
        assertThat(discovery.diagnostics()).isEmpty();
        assertThat(discovery.promptTemplates()).singleElement().satisfies(template ->
                assertThat(template.name()).isEqualTo("review"));
    }

    @Test
    void reportsDiagnosticAndSkipsSkillMissingRequiredDescription() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/skills/broken/SKILL.md"), """
                ---
                name: broken
                ---
                no description
                """);

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(discovery.skills()).isEmpty();
        assertThat(discovery.diagnostics()).singleElement().satisfies(diagnostic ->
                assertThat(diagnostic.message()).isEqualTo("skill missing required frontmatter: description"));
    }

    @Test
    void untrustedProjectSkipsProtectedProjectResourcesButKeepsContextFiles() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/settings.json"), """
                { "theme": "global", "prompts": ["global-prompts/*.md"] }
                """);
        write(home.resolve(".pi/agent/global-prompts/global.md"), "global prompt\n");
        write(home.resolve(".pi/agent/SYSTEM.md"), "global system\n");
        write(cwd.resolve(".pi/settings.json"), """
                { "theme": "project", "prompts": ["project-prompts/*.md"] }
                """);
        write(cwd.resolve(".pi/project-prompts/project.md"), "project prompt\n");
        write(cwd.resolve(".pi/SYSTEM.md"), "project system\n");
        write(cwd.resolve(".pi/APPEND_SYSTEM.md"), "project append\n");
        write(cwd.resolve(".pi/skills/project/SKILL.md"), """
                ---
                name: project
                description: Project skill
                ---
                project
                """);
        write(cwd.resolve("AGENTS.md"), "context stays loaded\n");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd)
                .withProjectTrustPolicy(ProjectTrustPolicy.UNTRUSTED));

        assertThat(discovery.settingsFiles()).extracting(SettingsFile::scope)
                .containsExactly(ResourceScope.GLOBAL);
        assertThat(discovery.settings().textField("theme")).contains("global");
        assertThat(discovery.contextFiles()).extracting(ResourceFile::content)
                .contains("context stays loaded\n");
        assertThat(discovery.systemPrompt()).hasValueSatisfying(system ->
                assertThat(system.content()).isEqualTo("global system\n"));
        assertThat(discovery.appendSystemFiles()).isEmpty();
        assertThat(discovery.promptTemplates()).extracting(PromptTemplate::name)
                .containsExactly("global");
        assertThat(discovery.skills()).isEmpty();
        assertThat(discovery.diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.message()).isEqualTo("project resources ignored because project is not trusted"));
    }

    @Test
    void loadsThemesFromDefaultDirectoriesSettingsAndCliSources() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/themes/solarized.json"), """
                { "colors": { "background": "#002b36" } }
                """);
        write(cwd.resolve(".pi/themes/solarized.json"), """
                { "colors": { "background": "#fdf6e3" } }
                """);
        write(cwd.resolve(".pi/settings.json"), """
                { "themes": ["extra-themes/*.json", "!extra-themes/private.json"] }
                """);
        write(cwd.resolve(".pi/extra-themes/team.json"), """
                { "colors": { "accent": "#268bd2" } }
                """);
        write(cwd.resolve(".pi/extra-themes/private.json"), """
                { "colors": { "accent": "#dc322f" } }
                """);
        write(cwd.resolve("cli-theme.json"), """
                { "colors": { "accent": "#859900" } }
                """);

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd)
                .withThemeSources(List.of("cli-theme.json")));

        assertThat(discovery.themes()).extracting(Theme::name)
                .containsExactly("solarized", "team", "cli-theme");
        Theme solarized = discovery.themes().getFirst();
        assertThat(solarized.scope()).isEqualTo(ResourceScope.PROJECT);
        assertThat(solarized.definition().at("/colors/background").asText()).isEqualTo("#fdf6e3");
        assertThat(discovery.themes()).noneMatch(theme -> theme.name().equals("private"));
        assertThat(discovery.themes().getLast().scope()).isEqualTo(ResourceScope.CURRENT);
    }

    @Test
    void canDisableThemeDiscovery() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        write(home.resolve(".pi/agent/themes/global.json"), "{}");
        write(cwd.resolve(".pi/themes/project.json"), "{}");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd).withoutThemes());

        assertThat(discovery.themes()).isEmpty();
    }

    @Test
    void loadsLocalPackagePromptSkillAndThemeResources() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        Path packageDir = tempDir.resolve("packages/shared");
        write(home.resolve(".pi/agent/settings.json"), """
                { "packages": ["%s"] }
                """.formatted(packageDir));
        write(packageDir.resolve("package.json"), """
                {
                  "name": "shared",
                  "pi": {
                    "prompts": ["prompts/*.md"],
                    "skills": ["skills"],
                    "themes": ["themes"]
                  }
                }
                """);
        write(packageDir.resolve("prompts/review.md"), "package review\n");
        writeSkill(packageDir.resolve("skills/package/SKILL.md"), "package-skill", "Package skill");
        write(packageDir.resolve("themes/package-theme.json"), "{}");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));

        assertThat(discovery.promptTemplates()).extracting(PromptTemplate::name)
                .containsExactly("review");
        assertThat(discovery.skills()).extracting(Skill::name)
                .containsExactly("package-skill");
        assertThat(discovery.themes()).extracting(Theme::name)
                .containsExactly("package-theme");
    }

    @Test
    void canDisablePackageResourcesAndReportsUnsupportedPackageSources() throws Exception {
        Path home = tempDir.resolve("home");
        Path cwd = tempDir.resolve("repo");
        Path packageDir = tempDir.resolve("packages/shared");
        write(home.resolve(".pi/agent/settings.json"), """
                { "packages": ["%s", "npm:remote-package"] }
                """.formatted(packageDir));
        write(packageDir.resolve("prompts/review.md"), "package review\n");

        ResourceDiscovery discovery = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd).withoutPackages());

        assertThat(discovery.promptTemplates()).isEmpty();
        assertThat(discovery.diagnostics()).isEmpty();

        ResourceDiscovery withPackages = loader.discover(ResourceDiscoveryOptions.enabled(home, cwd));
        assertThat(withPackages.promptTemplates()).extracting(PromptTemplate::name)
                .containsExactly("review");
        assertThat(withPackages.diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.message()).isEqualTo("unsupported package source ignored: npm:remote-package"));
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static void writeSkill(Path path, String name, String description) throws Exception {
        write(path, """
                ---
                name: %s
                description: %s
                ---
                # %s
                """.formatted(name, description, name));
    }
}
