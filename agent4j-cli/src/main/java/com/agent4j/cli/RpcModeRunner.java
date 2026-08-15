package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.CreateSessionRequest;
import com.agent4j.coding.session.SessionManager;
import com.agent4j.core.event.EventSubscription;
import com.agent4j.core.message.AgentMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/** Implements the PI-compatible JSONL RPC framing over stdin and stdout. */
public final class RpcModeRunner {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final Path temporaryDirectory;
    private final ObjectMapper mapper;
    private final JsonEventSerializer eventSerializer;

    public RpcModeRunner() {
        this(Path.of(System.getProperty("java.io.tmpdir")), new ObjectMapper(), new JsonEventSerializer());
    }

    RpcModeRunner(Path temporaryDirectory, ObjectMapper mapper, JsonEventSerializer eventSerializer) {
        this.temporaryDirectory = Objects.requireNonNull(temporaryDirectory, "temporaryDirectory").toAbsolutePath().normalize();
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.eventSerializer = Objects.requireNonNull(eventSerializer, "eventSerializer");
    }

    public int run(CliRuntime runtime, CliEnvironment environment, Reader input, PrintWriter out, PrintWriter err) {
        return run(runtime, environment, input, out, err, null);
    }

    int run(CliRuntime runtime, CliEnvironment environment, Reader input, PrintWriter out, PrintWriter err, CliSessionLifecycle lifecycle) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        Object outputLock = new Object();
        Path sessionDirectory = null;
        OwnedTemporaryDirectory ownedSessionDirectory = null;
        EventSubscription subscription = null;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AgentSession initialSession;
            if (lifecycle == null) {
                ownedSessionDirectory = OwnedTemporaryDirectory.create(temporaryDirectory, "agent4j-rpc-");
                sessionDirectory = ownedSessionDirectory.path();
                initialSession = createSession(runtime, environment, sessionDirectory);
            } else {
                initialSession = lifecycle.open();
                sessionDirectory = lifecycle.workingDirectory();
            }
            State state = new State(runtime, environment, sessionDirectory, outputLock, out, err, executor);
            state.session.set(initialSession);
            subscription = runtime.runtime().subscribe(event -> {
                AgentSession session = state.session.get();
                if (session != null && session.id().equals(event.sessionId())) {
                    write(out, outputLock, eventSerializer.serialize(eventSerializer.event(event)));
                }
            });

            try (BufferedReader reader = new BufferedReader(input)) {
                String line;
                while ((line = reader.readLine()) != null && !state.shutdown.get()) {
                    handleLine(line, state);
                }
            }
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                return 1;
            }
            return 0;
        } catch (Exception error) {
            err.println("Error: " + error.getMessage());
            return 1;
        } finally {
            if (subscription != null) {
                subscription.close();
            }
            executor.shutdownNow();
            if (ownedSessionDirectory != null) {
                ownedSessionDirectory.close();
            }
            out.flush();
            err.flush();
        }
    }

    private void handleLine(String line, State state) {
        if (line.isBlank()) {
            writeResponse(state, null, "parse", false, null, "empty JSONL record");
            return;
        }
        JsonNode request;
        try {
            request = mapper.readTree(line);
        } catch (JsonProcessingException error) {
            writeResponse(state, null, "parse", false, null, "invalid JSON: " + error.getOriginalMessage());
            return;
        }
        if (!request.isObject()) {
            writeResponse(state, null, "parse", false, null, "RPC request must be a JSON object");
            return;
        }
        JsonNode id = request.get("id");
        String command = request.path("type").asText();
        if (command.isBlank()) {
            writeResponse(state, id, "parse", false, null, "RPC request is missing textual type");
            return;
        }
        try {
            switch (command) {
                case "prompt" -> prompt(request, id, state);
                case "steer" -> queue(request, id, state, "steer");
                case "follow_up" -> queue(request, id, state, "follow_up");
                case "abort" -> {
                    state.abortActive(request.path("reason").asText("aborted by RPC request"));
                    writeResponse(state, id, command, true, null, null);
                }
                case "new_session" -> newSession(id, state);
                case "get_state" -> writeResponse(state, id, command, true, stateData(state), null);
                case "get_messages" -> writeResponse(state, id, command, true, messagesData(state), null);
                case "set_session_name" -> setSessionName(request, id, state);
                case "shutdown" -> {
                    state.shutdown.set(true);
                    state.abortActiveIfPresent("RPC shutdown");
                    writeResponse(state, id, command, true, null, null);
                }
                default -> writeResponse(state, id, command, false, null, "unsupported RPC command: " + command);
            }
        } catch (Exception error) {
            writeResponse(state, id, command, false, null, error.getMessage());
        }
    }

    private void prompt(JsonNode request, JsonNode id, State state) {
        String message = requiredMessage(request);
        if (state.streaming.get()) {
            String behavior = request.path("streamingBehavior").asText();
            if ("steer".equals(behavior)) {
                state.activeSession("prompt").steer(message);
            } else if ("followUp".equals(behavior)) {
                state.activeSession("prompt").followUp(message);
            } else {
                throw new IllegalStateException("prompt requires streamingBehavior while the agent is running");
            }
            writeResponse(state, id, "prompt", true, null, null);
            return;
        }
        startPrompt(message, id, state);
    }

    private void queue(JsonNode request, JsonNode id, State state, String command) {
        AgentSession session = state.activeSession(command);
        String message = requiredMessage(request);
        if ("steer".equals(command)) {
            session.steer(message);
        } else {
            session.followUp(message);
        }
        writeResponse(state, id, command, true, null, null);
    }

    private void startPrompt(String message, JsonNode id, State state) {
        if (!state.streaming.compareAndSet(false, true)) {
            throw new IllegalStateException("agent is already running");
        }
        writeResponse(state, id, "prompt", true, null, null);
        state.executor.submit(() -> {
            try {
                runPrompt(message, state);
            } catch (Exception error) {
                state.err.println("Error: " + error.getMessage());
                state.err.flush();
            } finally {
                state.streaming.set(false);
            }
        });
    }

    private static void runPrompt(String message, State state) throws Exception {
        state.session.get().prompt(CliPromptRequestFactory.create(
                message, state.runtime.defaultModel(), Optional.empty()));
    }

    private void newSession(JsonNode id, State state) throws Exception {
        if (state.streaming.get()) {
            throw new IllegalStateException("new_session requires the agent to be idle");
        }
        state.session.set(createSession(state.runtime, state.environment, state.sessionDirectory));
        state.sessionName.set(null);
        writeResponse(state, id, "new_session", true, JSON.objectNode().put("cancelled", false), null);
    }

    private void setSessionName(JsonNode request, JsonNode id, State state) throws Exception {
        String name = requiredText(request, "name");
        SessionManager.open(state.session.get().sessionFile()).appendSessionInfo(name);
        state.sessionName.set(name);
        writeResponse(state, id, "set_session_name", true, null, null);
    }

    private ObjectNode stateData(State state) {
        AgentSession session = state.session.get();
        ObjectNode data = JSON.objectNode();
        data.set("model", JSON.objectNode()
                .put("provider", state.runtime.defaultModel().providerId())
                .put("id", state.runtime.defaultModel().modelId()));
        data.put("isStreaming", state.streaming.get());
        data.put("isCompacting", false);
        data.put("steeringMode", "one-at-a-time");
        data.put("followUpMode", "one-at-a-time");
        data.put("sessionFile", session.sessionFile().toString());
        data.put("sessionId", session.id());
        if (state.sessionName.get() != null) {
            data.put("sessionName", state.sessionName.get());
        }
        data.put("messageCount", session.conversationContext().transcriptMessages().size());
        data.put("pendingMessageCount", session.pendingMessageCount());
        return data;
    }

    private ObjectNode messagesData(State state) {
        ArrayNode messages = JSON.arrayNode();
        for (AgentMessage message : state.session.get().conversationContext().transcriptMessages()) {
            messages.add(eventSerializer.message(message));
        }
        return JSON.objectNode().set("messages", messages);
    }

    private static AgentSession createSession(CliRuntime runtime, CliEnvironment environment, Path directory) throws Exception {
        Path sessionFile = Files.createTempFile(directory, "session-", ".jsonl");
        Files.deleteIfExists(sessionFile);
        return runtime.runtime().createSession(new CreateSessionRequest(
                sessionFile, environment.cwd(), Optional.empty(), Optional.of(runtime.defaultModel())));
    }

    private void writeResponse(State state, JsonNode id, String command, boolean success, JsonNode data, String error) {
        ObjectNode response = JSON.objectNode();
        if (id != null && !id.isNull()) {
            response.set("id", id.deepCopy());
        }
        response.put("type", "response");
        response.put("command", command);
        response.put("success", success);
        if (data != null) {
            response.set("data", data);
        }
        if (error != null && !error.isBlank()) {
            response.put("error", error);
        }
        write(state.out, state.outputLock, eventSerializer.serialize(response));
    }

    private static String requiredMessage(JsonNode request) {
        return requiredText(request, "message");
    }

    private static String requiredText(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("RPC request requires non-blank textual " + field);
        }
        return value.asText();
    }

    private static void write(PrintWriter out, Object lock, String line) {
        synchronized (lock) {
            out.println(line);
            out.flush();
        }
    }

    private static final class State {
        private final CliRuntime runtime;
        private final CliEnvironment environment;
        private final Path sessionDirectory;
        private final Object outputLock;
        private final PrintWriter out;
        private final PrintWriter err;
        private final ExecutorService executor;
        private final AtomicReference<AgentSession> session = new AtomicReference<>();
        private final AtomicReference<String> sessionName = new AtomicReference<>();
        private final AtomicBoolean streaming = new AtomicBoolean();
        private final AtomicBoolean shutdown = new AtomicBoolean();

        private State(CliRuntime runtime, CliEnvironment environment, Path sessionDirectory, Object outputLock, PrintWriter out, PrintWriter err, ExecutorService executor) {
            this.runtime = runtime;
            this.environment = environment;
            this.sessionDirectory = sessionDirectory;
            this.outputLock = outputLock;
            this.out = out;
            this.err = err;
            this.executor = executor;
        }

        private void abortActive(String reason) {
            activeSession("abort").abort(reason);
        }

        private void abortActiveIfPresent(String reason) {
            if (!streaming.get()) {
                return;
            }
            try {
                activeSession("abort").abort(reason);
            } catch (IllegalStateException ignored) {
                // The prompt completed while shutdown was being processed.
            }
        }

        private AgentSession activeSession(String command) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                AgentSession active = session.get();
                if (active != null && active.isStreaming()) {
                    return active;
                }
                if (!streaming.get()) {
                    break;
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            throw new IllegalStateException(command + " requires an active prompt");
        }
    }
}
