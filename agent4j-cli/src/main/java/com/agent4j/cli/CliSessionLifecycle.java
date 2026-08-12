package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.CreateSessionRequest;
import com.agent4j.coding.sdk.ForkSessionRequest;
import com.agent4j.coding.sdk.ResumeSessionRequest;
import com.agent4j.coding.session.SessionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/** Resolves CLI flags into SDK session lifecycle operations. */
final class CliSessionLifecycle implements AutoCloseable {
    private final CliRuntime runtime;
    private final CliEnvironment environment;
    private final CliSessionOptions options;
    private Path temporaryDirectory;

    CliSessionLifecycle(CliRuntime runtime, CliEnvironment environment, CliSessionOptions options) {
        this.runtime = runtime;
        this.environment = environment;
        this.options = options;
        validate();
    }

    AgentSession open() throws Exception {
        if (options.noSession()) {
            temporaryDirectory = Files.createTempDirectory("agent4j-no-session-");
            return create(temporaryDirectory.resolve("session.jsonl"), Optional.empty());
        }
        if (options.fork().isPresent()) {
            AgentSession source = resume(resolve(options.fork().orElseThrow()));
            return runtime.sessionRuntime().forkSession(new ForkSessionRequest(source, newSessionFile(), Optional.empty()));
        }
        if (options.session().isPresent()) {
            AgentSession session = resume(resolve(options.session().orElseThrow()));
            appendName(session);
            return session;
        }
        if (options.continueSession() || options.resume()) {
            AgentSession session = resume(mostRecent());
            appendName(session);
            return session;
        }
        if (options.sessionId().isPresent()) {
            Optional<Path> existing = findById(options.sessionId().orElseThrow());
            if (existing.isPresent()) {
                AgentSession session = resume(existing.orElseThrow());
                appendName(session);
                return session;
            }
        }
        return create(newSessionFile(), options.sessionId());
    }

    Path directory() {
        return options.sessionDirectory().orElseGet(() -> environment.homeDirectory()
                .resolve(".pi/agent/sessions")
                .resolve("--" + environment.cwd().toString().replaceFirst("^[/\\\\]", "").replaceAll("[/\\\\:]", "-") + "--"));
    }

    Path workingDirectory() {
        return temporaryDirectory == null ? directory() : temporaryDirectory;
    }

    private AgentSession create(Path file, Optional<String> sessionId) throws Exception {
        return runtime.sessionRuntime().createSession(new CreateSessionRequest(
                file, environment.cwd(), options.name(), Optional.of(runtime.defaultModel()), sessionId));
    }

    private AgentSession resume(Path file) throws Exception {
        return runtime.sessionRuntime().resumeSession(new ResumeSessionRequest(file, Optional.empty(), Optional.of(runtime.defaultModel())));
    }

    private void appendName(AgentSession session) throws IOException {
        if (options.name().isPresent()) {
            SessionManager.open(session.sessionFile()).appendSessionInfo(options.name().orElseThrow());
        }
    }

    private Path resolve(String value) throws IOException {
        if (value.contains("/") || value.contains("\\") || value.endsWith(".jsonl")) {
            return environment.cwd().resolve(value).toAbsolutePath().normalize();
        }
        return findById(value).orElseThrow(() -> new IllegalArgumentException("no session found matching '" + value + "'"));
    }

    private Optional<Path> findById(String id) throws IOException {
        if (!Files.isDirectory(directory())) {
            return Optional.empty();
        }
        try (var files = Files.list(directory())) {
            return files.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .filter(path -> headerId(path).map(value -> value.equals(id) || value.startsWith(id)).orElse(false))
                    .max(Comparator.comparing(this::modified));
        }
    }

    private Path mostRecent() throws IOException {
        if (!Files.isDirectory(directory())) {
            throw new IllegalArgumentException("no sessions exist for " + environment.cwd());
        }
        try (var files = Files.list(directory())) {
            return files.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .max(Comparator.comparing(this::modified))
                    .orElseThrow(() -> new IllegalArgumentException("no sessions exist for " + environment.cwd()));
        }
    }

    private Optional<String> headerId(Path file) {
        try {
            return Optional.of(SessionManager.open(file).document().header().header().orElseThrow().id());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Instant modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException error) {
            return Instant.EPOCH;
        }
    }

    private Path newSessionFile() {
        return directory().resolve(UUID.randomUUID() + ".jsonl");
    }

    private void validate() {
        if (options.fork().isPresent() && (options.session().isPresent() || options.continueSession() || options.resume() || options.noSession())) {
            throw new IllegalArgumentException("--fork cannot be combined with --session, --continue, --resume, or --no-session");
        }
        if (options.sessionId().isPresent() && (options.session().isPresent() || options.continueSession() || options.resume())) {
            throw new IllegalArgumentException("--session-id cannot be combined with --session, --continue, or --resume");
        }
        options.name().ifPresent(name -> {
            if (name.isBlank()) {
                throw new IllegalArgumentException("--name requires a non-empty value");
            }
        });
    }

    @Override
    public void close() {
        if (temporaryDirectory == null || !Files.exists(temporaryDirectory)) {
            return;
        }
        try (var paths = Files.walk(temporaryDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary no-session cleanup must not change command outcome.
                }
            });
        } catch (IOException ignored) {
            // Temporary no-session cleanup must not change command outcome.
        }
    }
}
