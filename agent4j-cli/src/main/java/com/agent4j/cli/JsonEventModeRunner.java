package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.CreateSessionRequest;
import com.agent4j.coding.sdk.PromptRequest;
import com.agent4j.coding.session.SessionManager;
import com.agent4j.core.event.EventSubscription;
import com.agent4j.core.runtime.AbortSignal;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Runs one prompt and writes the PI JSON event stream to stdout. */
public final class JsonEventModeRunner {
    private static final int DEFAULT_MAX_TOOL_ROUNDS = 20;

    private final Path temporaryDirectory;
    private final JsonEventSerializer serializer;

    public JsonEventModeRunner() {
        this(Path.of(System.getProperty("java.io.tmpdir")), new JsonEventSerializer());
    }

    JsonEventModeRunner(Path temporaryDirectory, JsonEventSerializer serializer) {
        this.temporaryDirectory = Objects.requireNonNull(temporaryDirectory, "temporaryDirectory").toAbsolutePath().normalize();
        this.serializer = Objects.requireNonNull(serializer, "serializer");
    }

    public int run(
            CliRuntime runtime,
            CliEnvironment environment,
            List<String> messages,
            Optional<AbortSignal> abortSignal,
            PrintWriter out,
            PrintWriter err
    ) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(messages, "messages");
        abortSignal = abortSignal == null ? Optional.empty() : abortSignal;
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        String prompt = String.join(" ", messages).strip();
        if (prompt.isEmpty()) {
            err.println("Error: JSON mode requires a prompt");
            return 1;
        }

        Path sessionDirectory = null;
        EventSubscription subscription = null;
        try {
            sessionDirectory = Files.createTempDirectory(temporaryDirectory, "agent4j-json-");
            AgentSession session = runtime.sessionRuntime().createSession(new CreateSessionRequest(
                    sessionDirectory.resolve("session.jsonl"),
                    environment.cwd(),
                    Optional.empty(),
                    Optional.of(runtime.defaultModel())));
            writeHeader(session, out);
            subscription = runtime.sessionRuntime().subscribeSession(session.id(), event -> {
                out.println(serializer.serialize(serializer.event(event)));
                out.flush();
            });
            session.prompt(new PromptRequest(
                    prompt,
                    Optional.of(runtime.defaultModel()),
                    DEFAULT_MAX_TOOL_ROUNDS,
                    0,
                    Optional.empty(),
                    null,
                    java.util.Map.of(),
                    List.of(),
                    List.of(),
                    null,
                    null,
                    abortSignal));
            return 0;
        } catch (Exception error) {
            err.println("Error: " + error.getMessage());
            return 1;
        } finally {
            if (subscription != null) {
                subscription.close();
            }
            deleteRecursively(sessionDirectory);
            out.flush();
            err.flush();
        }
    }

    private void writeHeader(AgentSession session, PrintWriter out) throws IOException {
        SessionManager manager = SessionManager.open(session.sessionFile());
        out.println(serializer.serialize(manager.document().header().payload()));
        out.flush();
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException ignored) {
                    // A temporary JSON-mode session must not mask the prompt result.
                }
            });
        } catch (IOException ignored) {
            // A temporary JSON-mode session must not mask the prompt result.
        }
    }
}
