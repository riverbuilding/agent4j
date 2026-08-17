package com.agent4j.examples;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.sdk.CodingAgentConfig;
import com.agent4j.core.tool.ToolRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Provider-neutral environment inputs for opt-in live examples. */
public final class LiveExampleConfiguration {
    public static final String API_KEY = "AGENT4J_API_KEY";
    public static final String BASE_URL = "AGENT4J_BASE_URL";
    public static final String MODEL = "AGENT4J_MODEL";
    public static final String SWITCH_MODEL = "AGENT4J_SWITCH_MODEL";
    public static final String WORKSPACE = "AGENT4J_EXAMPLES_WORKSPACE";
    public static final String SESSION_DIRECTORY = "AGENT4J_EXAMPLES_SESSION_DIRECTORY";
    public static final String MAX_OUTPUT_TOKENS = "AGENT4J_EXAMPLES_MAX_OUTPUT_TOKENS";
    public static final String MAX_TOOL_ROUNDS = "AGENT4J_EXAMPLES_MAX_TOOL_ROUNDS";

    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 256;
    public static final int DEFAULT_MAX_TOOL_ROUNDS = 1;

    private final String apiKey;
    private final String model;
    private final Optional<AiModelReference> switchModel;
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
            Optional<AiModelReference> switchModel,
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
        this.switchModel = Objects.requireNonNull(switchModel, "switchModel");
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
        String apiKey = requireEnvironment(environment, API_KEY);
        String model = requireEnvironment(environment, MODEL);
        ManagedDirectory workspace = directory(environment, WORKSPACE, "agent4j-example-workspace-");
        ManagedDirectory sessionDirectory = directory(environment, SESSION_DIRECTORY, "agent4j-example-sessions-");
        return new LiveExampleConfiguration(
                apiKey, model, optionalEnvironment(environment, SWITCH_MODEL).map(LiveExampleConfiguration::modelReference),
                optionalEnvironment(environment, BASE_URL), workspace.path(), sessionDirectory.path(),
                positiveInt(environment, MAX_OUTPUT_TOKENS, DEFAULT_MAX_OUTPUT_TOKENS),
                positiveInt(environment, MAX_TOOL_ROUNDS, DEFAULT_MAX_TOOL_ROUNDS),
                workspace.temporary(), sessionDirectory.temporary());
    }

    public CodingAgentConfig toCodingAgentConfig() {
        return configBuilder().build();
    }

    public CodingAgentConfig toCodingAgentConfig(ToolRegistry toolRegistry) {
        return configBuilder().toolRegistry(toolRegistry).build();
    }

    private CodingAgentConfig.Builder configBuilder() {
        CodingAgentConfig.Builder config = CodingAgentConfig.builder(apiKey, model, workspace, sessionDirectory)
                .maxOutputTokens(maxOutputTokens)
                .ownsWorkspace(cleanupWorkspace)
                .ownsSessionDirectory(cleanupSessionDirectory);
        baseUrl.ifPresent(config::baseUrl);
        switchModel.ifPresent(config::additionalModel);
        return config;
    }

    public String model() {
        return model;
    }

    public AiModelReference requireSwitchModel() {
        return switchModel.orElseThrow(() -> new IllegalStateException(SWITCH_MODEL + " must be set before running this example"));
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

    private static ManagedDirectory directory(Map<String, String> environment, String name, String prefix) throws IOException {
        String configured = environment.get(name);
        if (configured == null || configured.isBlank()) {
            return new ManagedDirectory(Path.of(System.getProperty("java.io.tmpdir"), prefix + UUID.randomUUID()), true);
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        return new ManagedDirectory(path, false);
    }

    private static String requireEnvironment(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set before running a live example");
        }
        return value.strip();
    }

    private static Optional<String> optionalEnvironment(Map<String, String> environment, String name) {
        return Optional.ofNullable(environment.get(name)).map(String::strip).filter(value -> !value.isBlank());
    }

    private static AiModelReference modelReference(String value) {
        String[] parts = value.split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(SWITCH_MODEL + " must use provider/model form");
        }
        return new AiModelReference(parts[0], parts[1]);
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

    private record ManagedDirectory(Path path, boolean temporary) {
    }
}
