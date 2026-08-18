package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.CreateSessionRequest;
import com.agent4j.coding.session.SessionManager;
import com.agent4j.core.event.EventSubscription;
import com.agent4j.core.runtime.AbortSignal;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Runs one prompt and writes the PI JSON event stream to stdout. */
public final class JsonEventModeRunner {
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
        return run(runtime, environment, messages, abortSignal, out, err, null);
    }

    int run(
            CliRuntime runtime, CliEnvironment environment, List<String> messages, Optional<AbortSignal> abortSignal,
            PrintWriter out, PrintWriter err, CliSessionLifecycle lifecycle
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

        OwnedTemporaryDirectory sessionDirectory = null;
        EventSubscription subscription = null;
        try {
            AgentSession session;
            if (lifecycle == null) {
                sessionDirectory = OwnedTemporaryDirectory.create(temporaryDirectory, "agent4j-json-");
                session = runtime.runtime().createSession(new CreateSessionRequest(
                        sessionDirectory.path().resolve("session.jsonl"), environment.cwd(), Optional.empty(), Optional.of(runtime.defaultModel())));
            } else {
                session = lifecycle.open();
            }
            writeHeader(session, out);
            subscription = runtime.runtime().subscribeSession(session.id(), event -> {
                out.println(serializer.serialize(serializer.event(event)));
                out.flush();
            });
            session.prompt(CliPromptRequestFactory.create(
                    prompt, runtime.promptModel(), abortSignal));
            return 0;
        } catch (Exception error) {
            err.println("Error: " + error.getMessage());
            return 1;
        } finally {
            if (subscription != null) {
                subscription.close();
            }
            if (sessionDirectory != null) {
                sessionDirectory.close();
            }
            out.flush();
            err.flush();
        }
    }

    private void writeHeader(AgentSession session, PrintWriter out) throws IOException {
        SessionManager manager = SessionManager.open(session.sessionFile());
        out.println(serializer.serialize(manager.document().header().payload()));
        out.flush();
    }

}
