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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Resolves CLI flags into SDK session lifecycle operations. */
final class CliSessionLifecycle implements AutoCloseable {
    private final CliRuntime runtime;
    private final CliEnvironment environment;
    private final CliSessionOptions options;
    private OwnedTemporaryDirectory temporaryDirectory;

    CliSessionLifecycle(CliRuntime runtime, CliEnvironment environment, CliSessionOptions options) {
        this.runtime = runtime;
        this.environment = environment;
        this.options = options;
        CliSessionOptions.validate(options);
    }

    AgentSession open() throws Exception {
        if (options.noSession()) {
            temporaryDirectory = OwnedTemporaryDirectory.create("agent4j-no-session-");
            return create(temporaryDirectory.path().resolve("session.jsonl"), Optional.empty());
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

    AgentSession createNew() throws Exception {
        return create(newSessionFile(), Optional.empty());
    }

    AgentSession continueMostRecent() throws Exception {
        return resume(mostRecent());
    }

    AgentSession resume(String value) throws Exception {
        return resume(resolve(value));
    }

    AgentSession resume(String value, boolean confirmCrossProject) throws Exception {
        Path path = resolve(value);
        SessionManager manager = SessionManager.open(path);
        Path sessionCwd = Path.of(manager.document().header().header().orElseThrow().cwd()).toAbsolutePath().normalize();
        if (confirmCrossProject && !sessionCwd.equals(environment.cwd())) {
            AgentSession source = resume(path);
            return runtime.sessionRuntime().forkSession(new ForkSessionRequest(
                    source, newSessionFile(), Optional.empty(), Optional.of(environment.cwd())));
        }
        return resume(path);
    }

    boolean isCrossProject(String value) throws IOException {
        Path path = resolve(value);
        SessionManager manager = SessionManager.open(path);
        return !Path.of(manager.document().header().header().orElseThrow().cwd()).toAbsolutePath().normalize().equals(environment.cwd());
    }

    List<SessionCandidate> candidates() throws IOException {
        List<SessionCandidate> candidates = new ArrayList<>();
        addCandidates(directory(), candidates);
        Path global = environment.homeDirectory().resolve(".pi/agent/sessions");
        if (!global.equals(directory())) addCandidates(global, candidates);
        candidates.sort(Comparator.comparing(SessionCandidate::modified).reversed());
        return List.copyOf(candidates);
    }

    Path directory() {
        return options.sessionDirectory().orElseGet(() -> environment.homeDirectory()
                .resolve(".pi/agent/sessions")
                .resolve("--" + environment.cwd().toString().replaceFirst("^[/\\\\]", "").replaceAll("[/\\\\:]", "-") + "--"));
    }

    Path workingDirectory() {
        return temporaryDirectory == null ? directory() : temporaryDirectory.path();
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
        return candidates().stream()
                .filter(candidate -> candidate.id().equals(id) || candidate.id().startsWith(id))
                .map(SessionCandidate::path)
                .findFirst();
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

    private void addCandidates(Path root, List<SessionCandidate> candidates) throws IOException {
        if (!Files.isDirectory(root)) return;
        try (var files = Files.walk(root)) {
            files.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .forEach(path -> {
                        try {
                            SessionManager manager = SessionManager.open(path);
                            var header = manager.document().header().header().orElseThrow();
                            candidates.add(new SessionCandidate(path, header.id(), Path.of(header.cwd()), modified(path)));
                        } catch (Exception ignored) {
                            // Ignore malformed or concurrently removed session files in the picker.
                        }
                    });
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

    record SessionCandidate(Path path, String id, Path cwd, Instant modified) { }

    @Override
    public void close() {
        if (temporaryDirectory != null) {
            temporaryDirectory.close();
        }
    }
}
