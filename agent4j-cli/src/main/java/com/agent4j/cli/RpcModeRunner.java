package com.agent4j.cli;

import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.CreateSessionRequest;
import com.agent4j.coding.sdk.PromptRequest;
import com.agent4j.coding.session.SessionManager;
import com.agent4j.core.event.EventSubscription;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.runtime.AbortController;
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
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Implements the PI-compatible JSONL RPC framing over stdin and stdout. */
public final class RpcModeRunner {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final int DEFAULT_MAX_TOOL_ROUNDS = 20;

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
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        Object outputLock = new Object();
        Path sessionDirectory = null;
        EventSubscription subscription = null;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            sessionDirectory = Files.createTempDirectory(temporaryDirectory, "agent4j-rpc-");
            State state = new State(runtime, environment, sessionDirectory, outputLock, out, err, executor);
            state.session.set(createSession(runtime, environment, sessionDirectory));
            subscription = runtime.sessionRuntime().subscribe(event -> {
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
            deleteRecursively(sessionDirectory);
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
                case "steer" -> queue(request, id, state, state.steering, "steer");
                case "follow_up" -> queue(request, id, state, state.followUps, "follow_up");
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
                    state.abortActive("RPC shutdown");
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
                state.steering.addLast(message);
            } else if ("followUp".equals(behavior)) {
                state.followUps.addLast(message);
            } else {
                throw new IllegalStateException("prompt requires streamingBehavior while the agent is running");
            }
            writeResponse(state, id, "prompt", true, null, null);
            return;
        }
        startPrompt(message, id, state);
    }

    private void queue(JsonNode request, JsonNode id, State state, ConcurrentLinkedDeque<String> queue, String command) {
        if (!state.streaming.get()) {
            throw new IllegalStateException(command + " requires an active prompt");
        }
        queue.addLast(requiredMessage(request));
        writeResponse(state, id, command, true, null, null);
    }

    private void startPrompt(String message, JsonNode id, State state) {
        if (!state.streaming.compareAndSet(false, true)) {
            throw new IllegalStateException("agent is already running");
        }
        AbortController controller = new AbortController();
        state.abortController.set(controller);
        writeResponse(state, id, "prompt", true, null, null);
        state.executor.submit(() -> {
            try {
                runPrompt(message, controller, state);
                while (!controller.signal().aborted()) {
                    String queued = state.pollNextQueuedMessage();
                    if (queued == null) {
                        break;
                    }
                    runPrompt(queued, controller, state);
                }
            } catch (Exception error) {
                state.err.println("Error: " + error.getMessage());
                state.err.flush();
            } finally {
                state.abortController.compareAndSet(controller, null);
                state.streaming.set(false);
            }
        });
    }

    private static void runPrompt(String message, AbortController controller, State state) throws Exception {
        state.session.get().prompt(new PromptRequest(
                message,
                Optional.of(state.runtime.defaultModel()),
                DEFAULT_MAX_TOOL_ROUNDS,
                0,
                Optional.empty(),
                null,
                java.util.Map.of(),
                java.util.List.of(),
                java.util.List.of(),
                null,
                null,
                Optional.of(controller.signal())));
    }

    private void newSession(JsonNode id, State state) throws Exception {
        if (state.streaming.get()) {
            throw new IllegalStateException("new_session requires the agent to be idle");
        }
        state.session.set(createSession(state.runtime, state.environment, state.sessionDirectory));
        state.sessionName.set(null);
        state.steering.clear();
        state.followUps.clear();
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
        data.put("pendingMessageCount", state.steering.size() + state.followUps.size());
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
        return runtime.sessionRuntime().createSession(new CreateSessionRequest(
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

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException ignored) {
                    // RPC temporary-session cleanup must not mask protocol output.
                }
            });
        } catch (IOException ignored) {
            // RPC temporary-session cleanup must not mask protocol output.
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
        private final AtomicReference<AbortController> abortController = new AtomicReference<>();
        private final AtomicReference<String> sessionName = new AtomicReference<>();
        private final AtomicBoolean streaming = new AtomicBoolean();
        private final AtomicBoolean shutdown = new AtomicBoolean();
        private final ConcurrentLinkedDeque<String> steering = new ConcurrentLinkedDeque<>();
        private final ConcurrentLinkedDeque<String> followUps = new ConcurrentLinkedDeque<>();

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
            AbortController controller = abortController.get();
            if (controller != null) {
                controller.abort(reason);
            }
        }

        private String pollNextQueuedMessage() {
            String steeringMessage = steering.pollFirst();
            return steeringMessage != null ? steeringMessage : followUps.pollFirst();
        }
    }
}
