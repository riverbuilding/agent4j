package com.agent4j.examples;

import com.agent4j.ai.AiModelReference;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CodingAgentSession;
import com.agent4j.coding.sdk.PromptResult;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.EventSubscription;
import com.agent4j.core.runtime.AgentAbortException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** 05-live-session-control: manually steers, follows up with, and cancels real streamed provider runs. */
public final class LiveSessionControlExample {
    private LiveSessionControlExample() {
    }

    public static void main(String[] args) throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open();
        CodingAgentRuntime runtime = CodingAgentRuntime.create(configuration.toCodingAgentConfig());
        try (runtime; ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CodingAgentSession session = runtime.createSession("05-live-session-control.jsonl");
            System.out.println("Model: " + configuration.model());
            System.out.println("Session JSONL: " + session.sessionFile());
            System.out.println("Each stage starts a real streamed request. Send its command after text begins streaming.");

            try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
                 EventSubscription ignored = subscribe(runtime)) {
                runCompletionStage(session, executor, input, runtime.defaultModel(), "steering", "/steer ",
                        "Write twelve short numbered facts about the number twelve, one per line. "
                                + "Keep generating until all twelve are complete.");
                runCompletionStage(session, executor, input, runtime.defaultModel(), "follow-up", "/follow-up ",
                        "Write twelve short numbered facts about the number thirteen, one per line. "
                                + "Keep generating until all twelve are complete.");
                runCancellationStage(session, executor, input, runtime.defaultModel());
            }
        } finally {
            runtime.cleanupOwnedFiles();
        }
    }

    private static EventSubscription subscribe(CodingAgentRuntime runtime) {
        return runtime.subscribe(event -> {
            if (event instanceof AgentEvent.MessageUpdated updated
                    && "text_delta".equals(updated.delta().path("type").asText())) {
                System.out.print(updated.delta().path("delta").asText());
                System.out.flush();
            }
            if (event instanceof AgentEvent.QueueUpdated updated) {
                System.out.printf("%nQueue updated: %s=%d%n", updated.queueKind(), updated.size());
            }
            if (event instanceof AgentEvent.AgentAborted aborted) {
                System.out.printf("%nAgent aborted: %s%n", aborted.reason());
            }
        });
    }

    private static void runCompletionStage(
            CodingAgentSession session,
            ExecutorService executor,
            BufferedReader input,
            AiModelReference model,
            String name,
            String command,
            String prompt
    ) throws Exception {
        System.out.println("\n--- " + name + " ---");
        RunningPrompt running = start(session, executor, model, prompt);
        waitForStreaming(session);
        System.out.println("\nType " + command + "<message> and press Enter now:");
        String message = readCommand(input, command);
        while (true) {
            try {
                if ("steering".equals(name)) {
                    session.steer(message);
                } else {
                    session.followUp(message);
                }
                reportCompleted(running, running.future().get());
                return;
            } catch (IllegalStateException error) {
                if (!"no prompt is active for this session".equals(error.getMessage())) {
                    throw error;
                }
                reportCompletedBeforeControl(running, command);
                running = restart(session, executor, model, prompt, command);
            }
        }
    }

    private static void runCancellationStage(
            CodingAgentSession session,
            ExecutorService executor,
            BufferedReader input,
            AiModelReference model
    ) throws Exception {
        System.out.println("\n--- cancellation ---");
        String prompt = "Write twelve short numbered facts about the number fourteen, one per line. "
                + "Keep generating until all twelve are complete.";
        RunningPrompt running = start(session, executor, model, prompt);
        waitForStreaming(session);
        System.out.println("\nType /abort and press Enter now:");
        readCommand(input, "/abort");
        while (!session.abort("cancelled by 05-live-session-control")) {
            reportCompletedBeforeControl(running, "/abort");
            running = restart(session, executor, model, prompt, "/abort");
        }
        try {
            running.future().get();
            throw new IllegalStateException("the cancelled prompt completed normally");
        } catch (ExecutionException error) {
            if (!(error.getCause() instanceof AgentAbortException)) {
                throw error;
            }
            System.out.printf("Cancellation elapsed: %d ms%n", Duration.between(running.started(), Instant.now()).toMillis());
            System.out.println("Cancellation observed by the local session.");
        }
    }

    private static RunningPrompt start(
            CodingAgentSession session,
            ExecutorService executor,
            AiModelReference model,
            String prompt
    ) {
        Instant started = Instant.now();
        Future<PromptResult> future = executor.submit(() -> session.prompt(
                LiveExampleHelper.buildPromptRequest(model, prompt, 0)));
        return new RunningPrompt(future, started);
    }

    private static void waitForStreaming(CodingAgentSession session) throws InterruptedException {
        for (int attempt = 0; attempt < 500; attempt++) {
            if (session.isStreaming()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new IllegalStateException("the streamed prompt did not become active within five seconds");
    }

    private static RunningPrompt restart(
            CodingAgentSession session,
            ExecutorService executor,
            AiModelReference model,
            String prompt,
            String command
    ) throws InterruptedException {
        System.out.println("The stream ended before " + command + " could be applied. Restarting this stage now.");
        RunningPrompt running = start(session, executor, model, prompt);
        waitForStreaming(session);
        return running;
    }

    private static String readCommand(BufferedReader input, String command) throws Exception {
        String line = input.readLine();
        if (line == null) {
            throw new IllegalStateException("standard input closed before " + command + " was entered");
        }
        if (!line.startsWith(command)) {
            throw new IllegalArgumentException("expected " + command + " but received: " + line);
        }
        String message = line.substring(command.length()).strip();
        if (!"/abort".equals(command) && message.isBlank()) {
            throw new IllegalArgumentException(command + " requires a non-blank message");
        }
        return message;
    }

    private static void reportCompleted(RunningPrompt running, PromptResult result) {
        System.out.println("\nStream completed after the queued control was consumed.");
        LiveExampleHelper.printUsage(System.out, result);
        System.out.printf("Elapsed: %d ms%n", Duration.between(running.started(), Instant.now()).toMillis());
    }

    private static void reportCompletedBeforeControl(RunningPrompt running, String command) throws Exception {
        PromptResult result = running.future().get();
        System.out.println("\nStream completed before " + command + " was entered.");
        LiveExampleHelper.printUsage(System.out, result);
        System.out.printf("Elapsed: %d ms%n", Duration.between(running.started(), Instant.now()).toMillis());
    }

    private record RunningPrompt(Future<PromptResult> future, Instant started) {
    }
}
