package com.agent4j.coding.sdk;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiModelClient;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.core.tool.InMemoryToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveAgentSessionControlTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void steeringAddedDuringAnActiveRunIsConsumedByThatRun() throws Exception {
        CountDownLatch firstRoundStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRound = new CountDownLatch(1);
        AtomicInteger rounds = new AtomicInteger();
        AiModelClient model = (request, sink) -> {
            int round = rounds.incrementAndGet();
            if (round == 1) {
                firstRoundStarted.countDown();
                assertThat(releaseFirstRound.await(5, TimeUnit.SECONDS)).isTrue();
            }
            sink.accept(completed("assistant-" + round, round == 1 ? "first" : "after steer"));
        };
        AgentSession session = runtime(model).createSession(new CreateSessionRequest(
                temporaryDirectory.resolve("session.jsonl"), temporaryDirectory, java.util.Optional.empty(), java.util.Optional.empty()));
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> session.prompt(new PromptRequest("start")));
            assertThat(firstRoundStarted.await(5, TimeUnit.SECONDS)).isTrue();

            session.steer("continue with this detail");
            releaseFirstRound.countDown();
            PromptResult completed = result.get(5, TimeUnit.SECONDS);

            assertThat(rounds.get()).isEqualTo(2);
            assertThat(completed.loopResult().messages()).extracting(message -> message.textContent())
                    .contains("continue with this detail", "after steer");
            assertThat(session.isStreaming()).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void abortCancelsTheActiveRun() throws Exception {
        CountDownLatch firstRoundStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRound = new CountDownLatch(1);
        AiModelClient model = (request, sink) -> {
            firstRoundStarted.countDown();
            assertThat(releaseFirstRound.await(5, TimeUnit.SECONDS)).isTrue();
            sink.accept(completed("assistant", "late"));
        };
        AgentSession session = runtime(model).createSession(new CreateSessionRequest(
                temporaryDirectory.resolve("abort.jsonl"), temporaryDirectory, java.util.Optional.empty(), java.util.Optional.empty()));
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> session.prompt(new PromptRequest("start")));
            assertThat(firstRoundStarted.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(session.abort("cancelled by test")).isTrue();
            releaseFirstRound.countDown();

            assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS)).hasRootCauseMessage("cancelled by test");
            assertThat(session.isStreaming()).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void followUpAddedDuringAnActiveRunIsConsumedAfterTheCurrentTurn() throws Exception {
        CountDownLatch firstRoundStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRound = new CountDownLatch(1);
        AtomicInteger rounds = new AtomicInteger();
        AiModelClient model = (request, sink) -> {
            int round = rounds.incrementAndGet();
            if (round == 1) {
                firstRoundStarted.countDown();
                assertThat(releaseFirstRound.await(5, TimeUnit.SECONDS)).isTrue();
            }
            sink.accept(completed("assistant-" + round, round == 1 ? "first" : "after follow-up"));
        };
        AgentSession session = runtime(model).createSession(new CreateSessionRequest(
                temporaryDirectory.resolve("follow-up.jsonl"), temporaryDirectory, java.util.Optional.empty(), java.util.Optional.empty()));
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> session.prompt(new PromptRequest("start")));
            assertThat(firstRoundStarted.await(5, TimeUnit.SECONDS)).isTrue();

            session.followUp("add this after the answer");
            releaseFirstRound.countDown();
            PromptResult completed = result.get(5, TimeUnit.SECONDS);

            assertThat(rounds.get()).isEqualTo(2);
            assertThat(completed.loopResult().messages()).extracting(message -> message.textContent())
                    .contains("add this after the answer", "after follow-up");
        } finally {
            executor.shutdownNow();
        }
    }

    private CodingAgentSessionRuntime runtime(AiModelClient model) {
        return new CodingAgentSessionRuntime(CodingAgentRuntimeServices.builder()
                .modelClient(model)
                .toolRegistry(InMemoryToolRegistry.builder().build())
                .clock(Clock.systemUTC())
                .build());
    }

    private static AiStreamEvent.MessageCompleted completed(String id, String text) {
        return new AiStreamEvent.MessageCompleted(id,
                new AiAssistantMessage(List.of(new AiTextContent(text)), AiStopReason.STOP, AiUsage.zero()));
    }
}
