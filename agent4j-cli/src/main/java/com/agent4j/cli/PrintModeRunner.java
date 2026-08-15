package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.CreateSessionRequest;
import com.agent4j.coding.sdk.PromptResult;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.runtime.AbortSignal;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Runs one prompt through the SDK runtime and emits only the final text answer. */
public final class PrintModeRunner {
    private final Path temporaryDirectory;

    public PrintModeRunner() {
        this(Path.of(System.getProperty("java.io.tmpdir")));
    }

    PrintModeRunner(Path temporaryDirectory) {
        this.temporaryDirectory = Objects.requireNonNull(temporaryDirectory, "temporaryDirectory").toAbsolutePath().normalize();
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
            err.println("Error: print mode requires a prompt");
            return 1;
        }

        OwnedTemporaryDirectory sessionDirectory = null;
        try {
            AgentSession session;
            if (lifecycle == null) {
                sessionDirectory = OwnedTemporaryDirectory.create(temporaryDirectory, "agent4j-print-");
                session = runtime.runtime().createSession(new CreateSessionRequest(
                        sessionDirectory.path().resolve("session.jsonl"), environment.cwd(), Optional.empty(), Optional.of(runtime.defaultModel())));
            } else {
                session = lifecycle.open();
            }
            PromptResult result = session.prompt(CliPromptRequestFactory.create(prompt, runtime.defaultModel(), abortSignal));
            finalAssistantText(result).ifPresent(out::println);
            return 0;
        } catch (Exception error) {
            err.println("Error: " + error.getMessage());
            return 1;
        } finally {
            if (sessionDirectory != null) {
                sessionDirectory.close();
            }
            out.flush();
            err.flush();
        }
    }

    private static Optional<String> finalAssistantText(PromptResult result) {
        return result.loopResult().assistantMessages().stream()
                .map(AgentMessage::textContent)
                .filter(text -> !text.isEmpty())
                .reduce((ignored, latest) -> latest);
    }

}
