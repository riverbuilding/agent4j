package com.agent4j.coding.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ResourceLoader {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final ObjectMapper mapper;

    public ResourceLoader() {
        this(new ObjectMapper());
    }

    public ResourceLoader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ResourceDiscovery discover(ResourceDiscoveryOptions options) throws IOException {
        AgentResourceDirectories directories = directories(options);
        List<ResourceDiagnostic> diagnostics = new ArrayList<>();
        boolean projectTrusted = options.projectTrustPolicy() == ProjectTrustPolicy.TRUSTED;
        if (!projectTrusted && projectRequiresTrust(options, directories)) {
            diagnostics.add(new ResourceDiagnostic(
                    ResourceScope.PROJECT,
                    options.cwd(),
                    "project resources ignored because project is not trusted"));
        }
        List<SettingsFile> settingsFiles = loadSettingsFiles(directories, projectTrusted);
        AgentSettings settings = mergeSettings(settingsFiles);
        List<ResourceFile> contextFiles = options.contextFilesEnabled()
                ? loadContextFiles(options, directories.globalAgentDir())
                : List.of();
        Optional<ResourceFile> systemPrompt = loadSystemPrompt(directories, projectTrusted);
        List<ResourceFile> appendSystemFiles = loadAppendSystemFiles(directories, projectTrusted);
        List<PromptTemplate> promptTemplates = options.promptTemplatesEnabled()
                ? loadPromptTemplates(options, directories, settingsFiles, projectTrusted, diagnostics)
                : List.of();
        ResourceLoadResult<Skill> skills = options.skillsEnabled()
                ? loadSkills(options, directories, settingsFiles, projectTrusted)
                : ResourceLoadResult.empty();
        diagnostics.addAll(skills.diagnostics());
        List<Theme> themes = options.themesEnabled()
                ? loadThemes(options, directories, settingsFiles, projectTrusted, diagnostics)
                : List.of();
        return new ResourceDiscovery(
                directories,
                settingsFiles,
                settings,
                contextFiles,
                systemPrompt,
                appendSystemFiles,
                promptTemplates,
                skills.items(),
                themes,
                diagnostics);
    }

    public AgentResourceDirectories directories(ResourceDiscoveryOptions options) {
        return new AgentResourceDirectories(
                options.homeDir().resolve(".pi").resolve("agent"),
                options.cwd().resolve(".pi"));
    }

    private List<ResourceFile> loadContextFiles(ResourceDiscoveryOptions options, Path globalAgentDir) throws IOException {
        List<ResourceFile> files = new ArrayList<>();
        addIfExists(files, ResourceScope.GLOBAL, ResourceFileType.AGENTS, globalAgentDir.resolve("AGENTS.md"));

        List<Path> directories = ancestorsRootFirst(options.cwd());
        for (int i = 0; i < directories.size(); i++) {
            Path directory = directories.get(i);
            ResourceScope scope = i == directories.size() - 1 ? ResourceScope.CURRENT : ResourceScope.PARENT;
            Optional<ResourceFile> contextFile = firstExistingContextFile(scope, directory);
            if (contextFile.isPresent()) {
                files.add(contextFile.orElseThrow());
            }
        }
        return List.copyOf(files);
    }

    private List<SettingsFile> loadSettingsFiles(AgentResourceDirectories directories, boolean projectTrusted) throws IOException {
        List<SettingsFile> files = new ArrayList<>();
        addSettingsIfExists(files, ResourceScope.GLOBAL, directories.globalAgentDir().resolve("settings.json"));
        if (projectTrusted) {
            addSettingsIfExists(files, ResourceScope.PROJECT, directories.projectAgentDir().resolve("settings.json"));
        }
        return List.copyOf(files);
    }

    private void addSettingsIfExists(List<SettingsFile> files, ResourceScope scope, Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            JsonNode parsed = mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
            if (!parsed.isObject()) {
                throw new IOException("settings file must contain a JSON object: " + path);
            }
            files.add(new SettingsFile(scope, path, (ObjectNode) parsed));
        }
    }

    private static AgentSettings mergeSettings(List<SettingsFile> settingsFiles) {
        ObjectNode merged = JSON.objectNode();
        for (SettingsFile settingsFile : settingsFiles) {
            mergeObject(merged, settingsFile.settings());
        }
        return new AgentSettings(merged);
    }

    private static void mergeObject(ObjectNode target, ObjectNode source) {
        source.fields().forEachRemaining(field -> {
            JsonNode existing = target.get(field.getKey());
            JsonNode incoming = field.getValue();
            if (existing != null && existing.isObject() && incoming.isObject()) {
                mergeObject((ObjectNode) existing, (ObjectNode) incoming);
            } else {
                target.set(field.getKey(), incoming.deepCopy());
            }
        });
    }

    private Optional<ResourceFile> firstExistingContextFile(ResourceScope scope, Path directory) throws IOException {
        Path agents = directory.resolve("AGENTS.md");
        if (Files.isRegularFile(agents)) {
            return Optional.of(read(scope, ResourceFileType.AGENTS, agents));
        }
        Path claude = directory.resolve("CLAUDE.md");
        if (Files.isRegularFile(claude)) {
            return Optional.of(read(scope, ResourceFileType.CLAUDE, claude));
        }
        return Optional.empty();
    }

    private Optional<ResourceFile> loadSystemPrompt(AgentResourceDirectories directories, boolean projectTrusted) throws IOException {
        Path projectSystem = directories.projectAgentDir().resolve("SYSTEM.md");
        if (projectTrusted && Files.isRegularFile(projectSystem)) {
            return Optional.of(read(ResourceScope.PROJECT, ResourceFileType.SYSTEM, projectSystem));
        }
        Path globalSystem = directories.globalAgentDir().resolve("SYSTEM.md");
        if (Files.isRegularFile(globalSystem)) {
            return Optional.of(read(ResourceScope.GLOBAL, ResourceFileType.SYSTEM, globalSystem));
        }
        return Optional.empty();
    }

    private List<ResourceFile> loadAppendSystemFiles(AgentResourceDirectories directories, boolean projectTrusted) throws IOException {
        List<ResourceFile> files = new ArrayList<>();
        addIfExists(files, ResourceScope.GLOBAL, ResourceFileType.APPEND_SYSTEM, directories.globalAgentDir().resolve("APPEND_SYSTEM.md"));
        if (projectTrusted) {
            addIfExists(files, ResourceScope.PROJECT, ResourceFileType.APPEND_SYSTEM, directories.projectAgentDir().resolve("APPEND_SYSTEM.md"));
        }
        return List.copyOf(files);
    }

    private List<PromptTemplate> loadPromptTemplates(
            ResourceDiscoveryOptions options,
            AgentResourceDirectories directories,
            List<SettingsFile> settingsFiles,
            boolean projectTrusted,
            List<ResourceDiagnostic> diagnostics
    ) throws IOException {
        Map<String, PromptTemplate> templates = new LinkedHashMap<>();
        loadPromptTemplateDirectory(templates, ResourceScope.GLOBAL, directories.globalAgentDir().resolve("prompts"));
        if (projectTrusted) {
            loadPromptTemplateDirectory(templates, ResourceScope.PROJECT, directories.projectAgentDir().resolve("prompts"));
        }
        for (SettingsFile settingsFile : settingsFiles) {
            for (String source : new AgentSettings(settingsFile.settings()).textArrayField("prompts")) {
                loadPromptTemplateSource(templates, settingsFile.scope(), settingsFile.path().getParent(), source);
            }
        }
        if (options.packagesEnabled()) {
            for (PackageResource packageResource : loadPackageResources(settingsFiles, diagnostics)) {
                for (String source : packageResource.sources("prompts", "prompts")) {
                    loadPromptTemplateSource(templates, packageResource.scope(), packageResource.root(), source);
                }
            }
        }
        return List.copyOf(templates.values());
    }

    private ResourceLoadResult<Skill> loadSkills(
            ResourceDiscoveryOptions options,
            AgentResourceDirectories directories,
            List<SettingsFile> settingsFiles,
            boolean projectTrusted
    ) throws IOException {
        Map<String, Skill> skills = new LinkedHashMap<>();
        List<ResourceDiagnostic> diagnostics = new ArrayList<>();

        if (projectTrusted) {
            loadSkillDirectory(skills, diagnostics, ResourceScope.PROJECT, directories.projectAgentDir().resolve("skills"), true);
            for (Path directory : ancestorsRootFirst(options.cwd())) {
                loadSkillDirectory(skills, diagnostics, ResourceScope.PROJECT, directory.resolve(".agents").resolve("skills"), false);
            }
            for (SettingsFile settingsFile : settingsFiles) {
                if (settingsFile.scope() == ResourceScope.PROJECT) {
                    loadSkillSources(skills, diagnostics, settingsFile);
                }
            }
        }
        loadSkillDirectory(skills, diagnostics, ResourceScope.GLOBAL, directories.globalAgentDir().resolve("skills"), true);
        loadSkillDirectory(skills, diagnostics, ResourceScope.GLOBAL, options.homeDir().resolve(".agents").resolve("skills"), false);
        for (SettingsFile settingsFile : settingsFiles) {
            if (settingsFile.scope() == ResourceScope.GLOBAL) {
                loadSkillSources(skills, diagnostics, settingsFile);
            }
        }
        if (options.packagesEnabled()) {
            for (PackageResource packageResource : loadPackageResources(settingsFiles, diagnostics)) {
                for (String source : packageResource.sources("skills", "skills")) {
                    loadSkillSource(skills, diagnostics, packageResource.scope(), packageResource.root(), source);
                }
            }
        }
        return new ResourceLoadResult<>(List.copyOf(skills.values()), List.copyOf(diagnostics));
    }

    private void loadSkillSources(
            Map<String, Skill> skills,
            List<ResourceDiagnostic> diagnostics,
            SettingsFile settingsFile
    ) throws IOException {
        for (String source : new AgentSettings(settingsFile.settings()).textArrayField("skills")) {
            loadSkillSource(skills, diagnostics, settingsFile.scope(), settingsFile.path().getParent(), source);
        }
    }

    private void loadSkillSource(
            Map<String, Skill> skills,
            List<ResourceDiagnostic> diagnostics,
            ResourceScope scope,
            Path baseDir,
            String source
    ) throws IOException {
        if (source == null || source.isBlank() || source.startsWith("!")) {
            return;
        }
        String normalizedSource = source.startsWith("+") ? source.substring(1) : source;
        Path path = resolveResourcePath(baseDir, normalizedSource);
        if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".md")) {
            addSkill(skills, diagnostics, readSkill(scope, path, path.getParent(), diagnostics));
        } else if (Files.isDirectory(path)) {
            loadSkillDirectory(skills, diagnostics, scope, path, true);
        }
    }

    private void loadSkillDirectory(
            Map<String, Skill> skills,
            List<ResourceDiagnostic> diagnostics,
            ResourceScope scope,
            Path directory,
            boolean includeRootMarkdownFiles
    ) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        if (includeRootMarkdownFiles) {
            try (var stream = Files.list(directory)) {
                List<Path> rootMarkdownFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".md"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
                for (Path skillFile : rootMarkdownFiles) {
                    addSkill(skills, diagnostics, readSkill(scope, skillFile, skillFile.getParent(), diagnostics));
                }
            }
        }
        try (var stream = Files.walk(directory)) {
            List<Path> skillFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("SKILL.md"))
                    .sorted(Comparator.comparing(path -> directory.relativize(path).toString()))
                    .toList();
            for (Path skillFile : skillFiles) {
                addSkill(skills, diagnostics, readSkill(scope, skillFile, skillFile.getParent(), diagnostics));
            }
        }
    }

    private void addSkill(
            Map<String, Skill> skills,
            List<ResourceDiagnostic> diagnostics,
            Optional<Skill> skill
    ) {
        if (skill.isEmpty()) {
            return;
        }
        Skill value = skill.orElseThrow();
        if (skills.containsKey(value.name())) {
            diagnostics.add(new ResourceDiagnostic(
                    value.scope(),
                    value.path(),
                    "duplicate skill ignored: " + value.name()));
            return;
        }
        skills.put(value.name(), value);
    }

    private Optional<Skill> readSkill(
            ResourceScope scope,
            Path path,
            Path root,
            List<ResourceDiagnostic> diagnostics
    ) throws IOException {
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        ParsedFrontMatter parsed = parseFrontMatter(raw);
        Optional<String> name = parsed.field("name");
        Optional<String> description = parsed.field("description");
        if (name.isEmpty() || description.isEmpty()) {
            diagnostics.add(new ResourceDiagnostic(
                    scope,
                    path,
                    "skill missing required frontmatter: " + (name.isEmpty() ? "name" : "description")));
            return Optional.empty();
        }
        return Optional.of(new Skill(
                name.orElseThrow(),
                description.orElseThrow(),
                scope,
                path,
                root,
                parsed.content(),
                parsed.field("license"),
                parsed.field("compatibility"),
                parsed.field("allowed-tools")
                        .map(value -> List.of(value.split("\\s+")))
                        .orElseGet(List::of),
                parsed.field("disable-model-invocation")
                        .map(Boolean::parseBoolean)
                        .orElse(false)));
    }

    private void loadPromptTemplateSource(
            Map<String, PromptTemplate> templates,
            ResourceScope scope,
            Path baseDir,
            String source
    ) throws IOException {
        if (source == null || source.isBlank()) {
            return;
        }
        if (source.startsWith("!")) {
            removePromptTemplates(templates, baseDir, source.substring(1));
            return;
        }
        String normalizedSource = source.startsWith("+") ? source.substring(1) : source;
        Path path = resolveResourcePath(baseDir, normalizedSource);
        if (containsGlob(normalizedSource)) {
            loadPromptTemplateGlob(templates, scope, baseDir, normalizedSource);
        } else if (Files.isDirectory(path)) {
            loadPromptTemplateDirectory(templates, scope, path);
        } else if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".md")) {
            putPromptTemplate(templates, readPromptTemplate(scope, path));
        }
    }

    private void removePromptTemplates(Map<String, PromptTemplate> templates, Path baseDir, String source) {
        Path path = resolveResourcePath(baseDir, source);
        if (containsGlob(source)) {
            PathMatcher matcher = baseDir.getFileSystem().getPathMatcher("glob:" + source);
            templates.entrySet().removeIf(entry -> matcher.matches(baseDir.relativize(entry.getValue().path())));
        } else {
            Path normalized = path.toAbsolutePath().normalize();
            templates.entrySet().removeIf(entry -> entry.getValue().path().equals(normalized));
        }
    }

    private void loadPromptTemplateDirectory(
            Map<String, PromptTemplate> templates,
            ResourceScope scope,
            Path directory
    ) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.list(directory)) {
            List<Path> promptFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path promptFile : promptFiles) {
                putPromptTemplate(templates, readPromptTemplate(scope, promptFile));
            }
        }
    }

    private void loadPromptTemplateGlob(
            Map<String, PromptTemplate> templates,
            ResourceScope scope,
            Path baseDir,
            String source
    ) throws IOException {
        PathMatcher matcher = baseDir.getFileSystem().getPathMatcher("glob:" + source);
        if (!Files.isDirectory(baseDir)) {
            return;
        }
        try (var stream = Files.walk(baseDir)) {
            List<Path> promptFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .filter(path -> matcher.matches(baseDir.relativize(path)))
                    .sorted(Comparator.comparing(path -> baseDir.relativize(path).toString()))
                    .toList();
            for (Path promptFile : promptFiles) {
                putPromptTemplate(templates, readPromptTemplate(scope, promptFile));
            }
        }
    }

    private PromptTemplate readPromptTemplate(ResourceScope scope, Path path) throws IOException {
        ParsedPromptTemplate parsed = parsePromptTemplate(Files.readString(path, StandardCharsets.UTF_8));
        String filename = path.getFileName().toString();
        String name = filename.substring(0, filename.length() - ".md".length());
        return new PromptTemplate(name, scope, path, parsed.content(), parsed.description(), parsed.argumentHint());
    }

    private static void putPromptTemplate(Map<String, PromptTemplate> templates, PromptTemplate template) {
        templates.put(template.name(), template);
    }

    private List<Theme> loadThemes(
            ResourceDiscoveryOptions options,
            AgentResourceDirectories directories,
            List<SettingsFile> settingsFiles,
            boolean projectTrusted,
            List<ResourceDiagnostic> diagnostics
    ) throws IOException {
        Map<String, Theme> themes = new LinkedHashMap<>();
        loadThemeDirectory(themes, ResourceScope.GLOBAL, directories.globalAgentDir().resolve("themes"));
        if (projectTrusted) {
            loadThemeDirectory(themes, ResourceScope.PROJECT, directories.projectAgentDir().resolve("themes"));
        }
        for (SettingsFile settingsFile : settingsFiles) {
            for (String source : new AgentSettings(settingsFile.settings()).textArrayField("themes")) {
                loadThemeSource(themes, settingsFile.scope(), settingsFile.path().getParent(), source);
            }
        }
        if (options.packagesEnabled()) {
            for (PackageResource packageResource : loadPackageResources(settingsFiles, diagnostics)) {
                for (String source : packageResource.sources("themes", "themes")) {
                    loadThemeSource(themes, packageResource.scope(), packageResource.root(), source);
                }
            }
        }
        for (String source : options.themeSources()) {
            loadThemeSource(themes, ResourceScope.CURRENT, options.cwd(), source);
        }
        return List.copyOf(themes.values());
    }

    private void loadThemeSource(
            Map<String, Theme> themes,
            ResourceScope scope,
            Path baseDir,
            String source
    ) throws IOException {
        if (source == null || source.isBlank()) {
            return;
        }
        if (source.startsWith("!") || source.startsWith("-")) {
            removeThemes(themes, baseDir, source.substring(1));
            return;
        }
        String normalizedSource = source.startsWith("+") ? source.substring(1) : source;
        Path path = resolveResourcePath(baseDir, normalizedSource);
        if (containsGlob(normalizedSource)) {
            loadThemeGlob(themes, scope, baseDir, normalizedSource);
        } else if (Files.isDirectory(path)) {
            loadThemeDirectory(themes, scope, path);
        } else if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json")) {
            putTheme(themes, readTheme(scope, path));
        }
    }

    private void loadThemeDirectory(Map<String, Theme> themes, ResourceScope scope, Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.list(directory)) {
            List<Path> themeFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path themeFile : themeFiles) {
                putTheme(themes, readTheme(scope, themeFile));
            }
        }
    }

    private void loadThemeGlob(
            Map<String, Theme> themes,
            ResourceScope scope,
            Path baseDir,
            String source
    ) throws IOException {
        PathMatcher matcher = baseDir.getFileSystem().getPathMatcher("glob:" + source);
        if (!Files.isDirectory(baseDir)) {
            return;
        }
        try (var stream = Files.walk(baseDir)) {
            List<Path> themeFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> matcher.matches(baseDir.relativize(path)))
                    .sorted(Comparator.comparing(path -> baseDir.relativize(path).toString()))
                    .toList();
            for (Path themeFile : themeFiles) {
                putTheme(themes, readTheme(scope, themeFile));
            }
        }
    }

    private void removeThemes(Map<String, Theme> themes, Path baseDir, String source) {
        Path path = resolveResourcePath(baseDir, source);
        if (containsGlob(source)) {
            PathMatcher matcher = baseDir.getFileSystem().getPathMatcher("glob:" + source);
            themes.entrySet().removeIf(entry -> matcher.matches(baseDir.relativize(entry.getValue().path())));
        } else {
            Path normalized = path.toAbsolutePath().normalize();
            themes.entrySet().removeIf(entry -> entry.getValue().path().equals(normalized));
        }
    }

    private Theme readTheme(ResourceScope scope, Path path) throws IOException {
        JsonNode parsed = mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
        if (!parsed.isObject()) {
            throw new IOException("theme file must contain a JSON object: " + path);
        }
        String filename = path.getFileName().toString();
        String name = filename.substring(0, filename.length() - ".json".length());
        return new Theme(name, scope, path, (ObjectNode) parsed);
    }

    private static void putTheme(Map<String, Theme> themes, Theme theme) {
        themes.put(theme.name(), theme);
    }

    private List<PackageResource> loadPackageResources(
            List<SettingsFile> settingsFiles,
            List<ResourceDiagnostic> diagnostics
    ) throws IOException {
        List<PackageResource> packages = new ArrayList<>();
        for (SettingsFile settingsFile : settingsFiles) {
            JsonNode packagesNode = settingsFile.settings().get("packages");
            if (packagesNode == null || !packagesNode.isArray()) {
                continue;
            }
            for (JsonNode packageNode : packagesNode) {
                Optional<PackageResource> packageResource = loadPackageResource(settingsFile, packageNode, diagnostics);
                packageResource.ifPresent(packages::add);
            }
        }
        return List.copyOf(packages);
    }

    private Optional<PackageResource> loadPackageResource(
            SettingsFile settingsFile,
            JsonNode packageNode,
            List<ResourceDiagnostic> diagnostics
    ) throws IOException {
        String source;
        ObjectNode filters = null;
        if (packageNode.isTextual()) {
            source = packageNode.asText();
        } else if (packageNode.isObject() && packageNode.path("source").isTextual()) {
            source = packageNode.path("source").asText();
            filters = (ObjectNode) packageNode;
        } else {
            return Optional.empty();
        }
        if (!isLocalPackageSource(source)) {
            diagnostics.add(new ResourceDiagnostic(
                    settingsFile.scope(),
                    settingsFile.path(),
                    "unsupported package source ignored: " + source));
            return Optional.empty();
        }
        Path root = resolveResourcePath(settingsFile.path().getParent(), source);
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        ObjectNode manifest = JSON.objectNode();
        Path packageJson = root.resolve("package.json");
        if (Files.isRegularFile(packageJson)) {
            JsonNode parsed = mapper.readTree(Files.readString(packageJson, StandardCharsets.UTF_8));
            if (parsed.isObject() && parsed.path("pi").isObject()) {
                manifest = (ObjectNode) parsed.path("pi");
            }
        }
        return Optional.of(new PackageResource(settingsFile.scope(), root, manifest, filters));
    }

    private static boolean projectRequiresTrust(ResourceDiscoveryOptions options, AgentResourceDirectories directories) {
        Path projectDir = directories.projectAgentDir();
        if (Files.isRegularFile(projectDir.resolve("settings.json"))
                || Files.isRegularFile(projectDir.resolve("SYSTEM.md"))
                || Files.isRegularFile(projectDir.resolve("APPEND_SYSTEM.md"))
                || Files.isDirectory(projectDir.resolve("extensions"))
                || Files.isDirectory(projectDir.resolve("skills"))
                || Files.isDirectory(projectDir.resolve("prompts"))
                || Files.isDirectory(projectDir.resolve("themes"))) {
            return true;
        }
        for (Path directory : ancestorsRootFirst(options.cwd())) {
            if (Files.isDirectory(directory.resolve(".agents").resolve("skills"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLocalPackageSource(String source) {
        return source.startsWith("/")
                || source.startsWith("./")
                || source.startsWith("../")
                || source.startsWith("~/");
    }

    private void addIfExists(List<ResourceFile> files, ResourceScope scope, ResourceFileType type, Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            files.add(read(scope, type, path));
        }
    }

    private ResourceFile read(ResourceScope scope, ResourceFileType type, Path path) throws IOException {
        return new ResourceFile(scope, type, path, Files.readString(path, StandardCharsets.UTF_8));
    }

    private static ParsedPromptTemplate parsePromptTemplate(String raw) {
        ParsedFrontMatter parsed = parseFrontMatter(raw);
        if (parsed.fields().isEmpty()) {
            String firstLine = raw.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .findFirst()
                    .orElse("");
            return new ParsedPromptTemplate(raw, Optional.of(firstLine).filter(value -> !value.isBlank()), Optional.empty());
        }
        return new ParsedPromptTemplate(
                parsed.content(),
                parsed.field("description"),
                parsed.field("argument-hint"));
    }

    private static ParsedFrontMatter parseFrontMatter(String raw) {
        if (!raw.startsWith("---\n")) {
            return new ParsedFrontMatter(Map.of(), raw);
        }
        int end = raw.indexOf("\n---", 4);
        if (end < 0) {
            return new ParsedFrontMatter(Map.of(), raw);
        }
        String frontMatter = raw.substring(4, end);
        int contentStart = end + "\n---".length();
        if (contentStart < raw.length() && raw.charAt(contentStart) == '\n') {
            contentStart++;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : frontMatter.split("\n")) {
            int separator = line.indexOf(':');
            if (separator < 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = unquote(line.substring(separator + 1).trim());
            if (!key.isBlank() && !value.isBlank()) {
                fields.put(key, value);
            }
        }
        return new ParsedFrontMatter(Map.copyOf(fields), raw.substring(contentStart));
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Path resolveResourcePath(Path baseDir, String source) {
        String expanded = source.startsWith("~/")
                ? System.getProperty("user.home") + source.substring(1)
                : source;
        Path path = Path.of(expanded);
        return path.isAbsolute() ? path.normalize() : baseDir.resolve(path).normalize();
    }

    private static boolean containsGlob(String source) {
        return source.indexOf('*') >= 0 || source.indexOf('?') >= 0 || source.indexOf('[') >= 0 || source.indexOf('{') >= 0;
    }

    private static List<Path> ancestorsRootFirst(Path cwd) {
        List<Path> reversed = new ArrayList<>();
        Path current = cwd.toAbsolutePath().normalize();
        while (current != null) {
            reversed.add(current);
            current = current.getParent();
        }
        List<Path> rootFirst = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            rootFirst.add(reversed.get(i));
        }
        return rootFirst;
    }

    private record ParsedPromptTemplate(
            String content,
            Optional<String> description,
            Optional<String> argumentHint
    ) {
    }

    private record ParsedFrontMatter(Map<String, String> fields, String content) {
        Optional<String> field(String name) {
            return Optional.ofNullable(fields.get(name));
        }
    }

    private record ResourceLoadResult<T>(List<T> items, List<ResourceDiagnostic> diagnostics) {
        private static <T> ResourceLoadResult<T> empty() {
            return new ResourceLoadResult<>(List.of(), List.of());
        }
    }

    private record PackageResource(ResourceScope scope, Path root, ObjectNode manifest, ObjectNode filters) {
        private List<String> sources(String key, String conventionalDirectory) {
            if (filters != null && filters.has(key)) {
                return textArray(filters.get(key));
            }
            if (manifest.has(key)) {
                return textArray(manifest.get(key));
            }
            return Files.isDirectory(root.resolve(conventionalDirectory))
                    ? List.of(conventionalDirectory)
                    : List.of();
        }

        private static List<String> textArray(JsonNode node) {
            if (!node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            node.forEach(value -> {
                if (value.isTextual()) {
                    values.add(value.asText());
                }
            });
            return List.copyOf(values);
        }
    }
}
