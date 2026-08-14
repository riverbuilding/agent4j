package com.agent4j.examples;

import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.OpenAiCodingAgentConfig;
import com.agent4j.core.tool.ToolRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Environment-derived configuration and temporary paths for opt-in live examples. */
public final class LiveExampleConfiguration implements AutoCloseable {
    public static final String OPENAI_API_KEY = "OPENAI_API_KEY";
    public static final String OPENAI_BASE_URL = "OPENAI_BASE_URL";
    public static final String OPENAI_MODEL = "AGENT4J_OPENAI_MODEL";
    public static final String WORKSPACE = "AGENT4J_EXAMPLES_WORKSPACE";
    public static final String SESSION_DIRECTORY = "AGENT4J_EXAMPLES_SESSION_DIRECTORY";
    public static final String MAX_OUTPUT_TOKENS = "AGENT4J_EXAMPLES_MAX_OUTPUT_TOKENS";
    public static final String MAX_TOOL_ROUNDS = "AGENT4J_EXAMPLES_MAX_TOOL_ROUNDS";

    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 256;
    public static final int DEFAULT_MAX_TOOL_ROUNDS = 1;

    private final String apiKey;
    private final String model;
    private final Optional<String> baseUrl;
    private final Path workspace;
    private final Path sessionDirectory;
    private final int maxOutputTokens;
    private final int maxToolRounds;
    private final boolean cleanupWorkspace;
    private final boolean cleanupSessionDirectory;

    private LiveExampleConfiguration(
            String apiKey,
            String model,
            Optional<String> baseUrl,
            Path workspace,
            Path sessionDirectory,
            int maxOutputTokens,
            int maxToolRounds,
            boolean cleanupWorkspace,
            boolean cleanupSessionDirectory
    ) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.model = Objects.requireNonNull(model, "model");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.sessionDirectory = Objects.requireNonNull(sessionDirectory, "sessionDirectory");
        this.maxOutputTokens = maxOutputTokens;
        this.maxToolRounds = maxToolRounds;
        this.cleanupWorkspace = cleanupWorkspace;
        this.cleanupSessionDirectory = cleanupSessionDirectory;
    }

    public static LiveExampleConfiguration open() throws IOException {
        return open(System.getenv());
    }

    static LiveExampleConfiguration open(Map<String, String> environment) throws IOException {
        Objects.requireNonNull(environment, "environment");
        ManagedDirectory workspace = directory(environment, WORKSPACE, "agent4j-example-workspace-");
        ManagedDirectory sessionDirectory = directory(environment, SESSION_DIRECTORY, "agent4j-example-sessions-");
        try {
            return new LiveExampleConfiguration(
                    requireEnvironment(environment, OPENAI_API_KEY),
                    requireEnvironment(environment, OPENAI_MODEL),
                    optionalEnvironment(environment, OPENAI_BASE_URL),
                    workspace.path(),
                    sessionDirectory.path(),
                    positiveInt(environment, MAX_OUTPUT_TOKENS, DEFAULT_MAX_OUTPUT_TOKENS),
                    positiveInt(environment, MAX_TOOL_ROUNDS, DEFAULT_MAX_TOOL_ROUNDS),
                    workspace.temporary(),
                    sessionDirectory.temporary());
        } catch (RuntimeException error) {
            cleanup(workspace);
            cleanup(sessionDirectory);
            throw error;
        }
    }

    public CodingAgentRuntime createRuntime() {
        return CodingAgentRuntime.openAi(openAiConfig());
    }

    public CodingAgentRuntime createRuntime(ToolRegistry toolRegistry) {
        return CodingAgentRuntime.openAi(openAiConfig(toolRegistry));
    }

    OpenAiCodingAgentConfig openAiConfig() {
        return openAiConfigBuilder().build();
    }

    OpenAiCodingAgentConfig openAiConfig(ToolRegistry toolRegistry) {
        return openAiConfigBuilder()
                .toolRegistry(toolRegistry)
                .build();
    }

    private OpenAiCodingAgentConfig.Builder openAiConfigBuilder() {
        OpenAiCodingAgentConfig.Builder config = OpenAiCodingAgentConfig.builder(apiKey, model)
                .maxOutputTokens(maxOutputTokens);
        baseUrl.ifPresent(config::baseUrl);
        return config;
    }

    public String model() {
        return model;
    }

    public Optional<String> baseUrl() {
        return baseUrl;
    }

    public Path workspace() {
        return workspace;
    }

    public Path sessionDirectory() {
        return sessionDirectory;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public int maxToolRounds() {
        return maxToolRounds;
    }

    public boolean temporaryWorkspace() {
        return cleanupWorkspace;
    }

    public boolean temporarySessionDirectory() {
        return cleanupSessionDirectory;
    }

    public Path sessionFile(String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        Path name = Path.of(fileName);
        if (name.getNameCount() != 1 || !fileName.endsWith(".jsonl")) {
            throw new IllegalArgumentException("session file name must be a single .jsonl file name");
        }
        return sessionDirectory.resolve(name).normalize();
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        if (cleanupSessionDirectory) {
            failure = deleteDirectory(sessionDirectory, failure);
        }
        if (cleanupWorkspace) {
            failure = deleteDirectory(workspace, failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static ManagedDirectory directory(Map<String, String> environment, String name, String prefix) throws IOException {
        String configured = environment.get(name);
        if (configured == null || configured.isBlank()) {
            return new ManagedDirectory(Files.createTempDirectory(prefix), true);
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(path);
        return new ManagedDirectory(path, false);
    }

    private static String requireEnvironment(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set before running a live OpenAI example");
        }
        return value.strip();
    }

    private static Optional<String> optionalEnvironment(Map<String, String> environment, String name) {
        return Optional.ofNullable(environment.get(name)).map(String::strip).filter(value -> !value.isBlank());
    }

    private static int positiveInt(Map<String, String> environment, String name, int defaultValue) {
        String configured = environment.get(name);
        if (configured == null || configured.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(configured.strip());
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + " must be a positive integer", error);
        }
    }

    private static void cleanup(ManagedDirectory directory) {
        if (!directory.temporary()) {
            return;
        }
        try {
            deleteDirectory(directory.path(), null);
        } catch (IOException ignored) {
            // A failed constructor must retain the original setup error.
        }
    }

    private static IOException deleteDirectory(Path directory, IOException priorFailure) throws IOException {
        if (!Files.exists(directory)) {
            return priorFailure;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException error) {
            if (priorFailure == null) {
                return error;
            }
            priorFailure.addSuppressed(error);
        }
        return priorFailure;
    }

    private record ManagedDirectory(Path path, boolean temporary) {
    }
}
