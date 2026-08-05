package com.agent4j.coding.sdk;

import com.agent4j.ai.AiAuthMode;
import com.agent4j.ai.AiResolvedAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PersistentAuthCredentialStore implements AuthCredentialStore {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final int VERSION = 1;

    private final Path credentialFile;
    private final ObjectMapper mapper;

    public PersistentAuthCredentialStore(Path credentialFile) {
        this(credentialFile, new ObjectMapper());
    }

    public PersistentAuthCredentialStore(Path credentialFile, ObjectMapper mapper) {
        this.credentialFile = Objects.requireNonNull(credentialFile, "credentialFile").toAbsolutePath().normalize();
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public static PersistentAuthCredentialStore userDefault() {
        return forHome(Path.of(System.getProperty("user.home")));
    }

    public static PersistentAuthCredentialStore forHome(Path home) {
        Objects.requireNonNull(home, "home");
        return new PersistentAuthCredentialStore(home.resolve(".pi").resolve("agent").resolve("auth.json"));
    }

    public Path credentialFile() {
        return credentialFile;
    }

    @Override
    public synchronized Optional<AuthSession> find(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return Optional.ofNullable(readSessions().get(providerId));
    }

    @Override
    public synchronized void save(AuthSession session) {
        Objects.requireNonNull(session, "session");
        Map<String, AuthSession> sessions = readSessions();
        sessions.put(session.providerId(), session);
        writeSessions(sessions);
    }

    @Override
    public synchronized boolean delete(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        Map<String, AuthSession> sessions = readSessions();
        boolean removed = sessions.remove(providerId) != null;
        if (removed) {
            writeSessions(sessions);
        }
        return removed;
    }

    private Map<String, AuthSession> readSessions() {
        if (!Files.exists(credentialFile)) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode root = mapper.readTree(credentialFile.toFile());
            if (!root.isObject()) {
                throw new IllegalStateException("auth credential file must contain a JSON object: " + credentialFile);
            }
            JsonNode sessionsNode = root.path("sessions");
            if (sessionsNode.isMissingNode() || sessionsNode.isNull()) {
                return new LinkedHashMap<>();
            }
            if (!sessionsNode.isObject()) {
                throw new IllegalStateException("auth credential sessions must be a JSON object: " + credentialFile);
            }
            Map<String, AuthSession> sessions = new LinkedHashMap<>();
            sessionsNode.fields().forEachRemaining(entry -> sessions.put(entry.getKey(), session(entry.getKey(), entry.getValue())));
            return sessions;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read auth credential file: " + credentialFile, e);
        }
    }

    private void writeSessions(Map<String, AuthSession> sessions) {
        ObjectNode root = JSON.objectNode();
        root.put("version", VERSION);
        ObjectNode sessionsNode = JSON.objectNode();
        sessions.forEach((providerId, session) -> sessionsNode.set(providerId, session(session)));
        root.set("sessions", sessionsNode);
        try {
            Path parent = credentialFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
                restrictPermissions(parent, true);
            }
            Path temp = Files.createTempFile(parent, credentialFile.getFileName().toString(), ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), root);
            restrictPermissions(temp, false);
            try {
                Files.move(temp, credentialFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temp, credentialFile, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictPermissions(credentialFile, false);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write auth credential file: " + credentialFile, e);
        }
    }

    private static ObjectNode session(AuthSession session) {
        ObjectNode node = JSON.objectNode();
        node.put("providerId", session.providerId());
        node.put("mode", session.mode().wireName());
        node.put("authenticatedAt", session.authenticatedAt().toString());
        node.set("auth", auth(session.auth()));
        return node;
    }

    private static AuthSession session(String providerId, JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalStateException("auth session must be a JSON object for provider: " + providerId);
        }
        String mode = requiredText(node, "mode");
        Instant authenticatedAt = Instant.parse(requiredText(node, "authenticatedAt"));
        JsonNode authNode = node.path("auth");
        if (!authNode.isObject()) {
            throw new IllegalStateException("auth session is missing auth object for provider: " + providerId);
        }
        return new AuthSession(
                text(node.get("providerId")).orElse(providerId),
                AiAuthMode.fromWireName(mode),
                auth(authNode),
                authenticatedAt);
    }

    private static ObjectNode auth(AiResolvedAuth auth) {
        ObjectNode node = JSON.objectNode();
        node.put("mode", auth.mode().wireName());
        auth.apiKey().ifPresent(value -> node.put("apiKey", value));
        auth.accessToken().ifPresent(value -> node.put("accessToken", value));
        node.set("headers", stringObject(auth.headers()));
        auth.baseUrl().ifPresent(value -> node.put("baseUrl", value));
        auth.source().ifPresent(value -> node.put("source", value));
        auth.expiresAt().ifPresent(value -> node.put("expiresAt", value.toString()));
        node.set("environment", stringObject(auth.environment()));
        node.set("metadata", stringObject(auth.metadata()));
        return node;
    }

    private static AiResolvedAuth auth(JsonNode node) {
        return new AiResolvedAuth(
                AiAuthMode.fromWireName(requiredText(node, "mode")),
                text(node.get("apiKey")),
                text(node.get("accessToken")),
                stringMap(node.path("headers"), "headers"),
                text(node.get("baseUrl")),
                text(node.get("source")),
                text(node.get("expiresAt")).map(Instant::parse),
                stringMap(node.path("environment"), "environment"),
                stringMap(node.path("metadata"), "metadata"));
    }

    private static ObjectNode stringObject(Map<String, String> values) {
        ObjectNode node = JSON.objectNode();
        values.forEach(node::put);
        return node;
    }

    private static Map<String, String> stringMap(JsonNode node, String field) {
        if (node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalStateException("auth field must be a JSON object: " + field);
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual()) {
                throw new IllegalStateException("auth field must contain only strings: " + field);
            }
            values.put(entry.getKey(), entry.getValue().asText());
        });
        return values;
    }

    private static String requiredText(JsonNode node, String field) {
        return text(node.get(field))
                .orElseThrow(() -> new IllegalStateException("auth session is missing text field: " + field));
    }

    private static Optional<String> text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isTextual()) {
            throw new IllegalStateException("auth field must be text");
        }
        return Optional.of(node.asText());
    }

    private static void restrictPermissions(Path path, boolean directory) throws IOException {
        try {
            Set<PosixFilePermission> permissions = directory
                    ? PosixFilePermissions.fromString("rwx------")
                    : PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows and some mounted filesystems do not expose POSIX permissions.
        }
    }
}
