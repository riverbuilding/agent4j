package com.agent4j.coding.runtime;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiCost;
import com.agent4j.ai.AiInputType;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelCompat;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderApi;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiProviderSelection;
import com.agent4j.ai.AiResolvedAuth;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.ai.AiTextContent;
import com.agent4j.core.compaction.CompactionConfig;
import com.agent4j.core.compaction.CompactionResult;
import com.agent4j.core.compaction.CompactionService;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.coding.session.SessionEntry;
import com.agent4j.coding.session.SessionEntryType;
import com.agent4j.coding.session.SessionJsonlCodec;
import com.agent4j.coding.session.SessionManager;
import com.agent4j.coding.session.SessionMessageRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class CodingSessionCompactorTest {
    private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");

    @TempDir
    Path tempDir;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void manualCompactionEmitsEventsCompactsActiveMessagesAndPersistsResult() throws Exception {
        SessionManager sessionManager = sessionManager("compact-entry-1");
        sessionManager.appendUserMessage("Read README.md");
        sessionManager.appendAssistantText("README says hello");
        FakeProvider provider = new FakeProvider("summary from model");
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new java.util.ArrayList<>();
        bus.subscribe(events::add);
        CodingSessionCompactor compactor = new CodingSessionCompactor(new CompactionService(), bus, clock);

        CompactionResult result = compactor.compact(new ManualCompactionRequest(
                sessionManager,
                new AiProviderSelection(provider, model()),
                AiResolvedAuth.none(),
                tempDir,
                "system prompt",
                CompactionConfig.builder()
                        .keepTokens(0)
                        .keepMessages(1)
                        .summaryPrompt("Summarize:\n{messages}")
                        .build(),
                "preserve file details",
                AiStreamOptions.defaults()));

        assertThat(result.compacted()).isTrue();
        assertThat(result.retainedMessages()).extracting(message -> message.textContent())
                .containsExactly("README says hello");
        assertThat(provider.requests).hasSize(1);
        assertThat(provider.prompt()).isEqualTo("""
                Summarize:
                Human: Read README.md

                <focusInstructions>
                preserve file details
                </focusInstructions>""");
        assertThat(provider.requests.getFirst().context().sessionId()).contains(sessionId(sessionManager));
        assertThat(provider.requests.getFirst().context().cwd()).contains(tempDir.toAbsolutePath().normalize());
        assertThat(provider.requests.getFirst().context().attributes().get("compactionReason")).isEqualTo("manual");

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOfSatisfying(AgentEvent.CompactionStarted.class, event -> {
            assertThat(event.sessionId()).isEqualTo(sessionId(sessionManager));
            assertThat(event.timestamp()).isEqualTo(NOW);
            assertThat(event.reason()).isEqualTo("manual");
        });
        assertThat(events.get(1)).isInstanceOfSatisfying(AgentEvent.CompactionCompleted.class, event -> {
            assertThat(event.sessionId()).isEqualTo(sessionId(sessionManager));
            assertThat(event.timestamp()).isEqualTo(NOW);
            assertThat(event.summaryMessageId()).isEqualTo(result.summaryMessage().id());
        });

        assertThat(sessionManager.document().entries()).extracting(SessionEntry::type)
                .containsExactly(
                        SessionEntryType.MESSAGE,
                        SessionEntryType.MESSAGE,
                        SessionEntryType.MESSAGE,
                        SessionEntryType.COMPACTION);
        assertThat(sessionManager.document().entries().get(2).message().orElseThrow().role())
                .isEqualTo(SessionMessageRole.COMPACTION_SUMMARY);
        assertThat(sessionManager.document().entries().get(3).compaction().orElseThrow()
                .retainedEntries().get(0).asText()).isEqualTo(sessionManager.document().entries().get(1).id());
        assertThat(Files.readAllLines(sessionManager.sessionFile())).hasSize(5);
    }

    @Test
    void manualCompactionNoOpStillEmitsCompletedEventAndDoesNotPersist() throws Exception {
        SessionManager sessionManager = sessionManager("unused");
        sessionManager.appendUserMessage("short");
        FakeProvider provider = new FakeProvider("unused");
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new java.util.ArrayList<>();
        bus.subscribe(events::add);
        CodingSessionCompactor compactor = new CodingSessionCompactor(new CompactionService(), bus, clock);

        CompactionResult result = compactor.compact(new ManualCompactionRequest(
                sessionManager,
                new AiProviderSelection(provider, model()),
                AiResolvedAuth.none(),
                null,
                null,
                CompactionConfig.builder()
                        .keepTokens(0)
                        .keepMessages(10)
                        .build(),
                null,
                null));

        assertThat(result.compacted()).isFalse();
        assertThat(provider.requests).isEmpty();
        assertThat(sessionManager.document().entries()).extracting(SessionEntry::type)
                .containsExactly(SessionEntryType.MESSAGE);
        assertThat(events).hasSize(2);
        assertThat(events.get(1)).isInstanceOfSatisfying(AgentEvent.CompactionCompleted.class, event ->
                assertThat(event.summaryMessageId()).isNull());
    }

    private SessionManager sessionManager(String nextId) throws Exception {
        AtomicInteger ids = new AtomicInteger();
        return SessionManager.create(
                tempDir.resolve(nextId + ".jsonl"),
                tempDir,
                new SessionJsonlCodec(),
                () -> nextId + "-" + ids.incrementAndGet(),
                clock);
    }

    private static String sessionId(SessionManager manager) {
        return manager.document().header().header().orElseThrow().id();
    }

    private static AiModel model() {
        return new AiModel(
                new AiModelReference("fake", "compact-model"),
                "Compact Model",
                Optional.of(AiProviderApi.CUSTOM),
                Optional.empty(),
                false,
                Map.of(),
                Set.of(),
                EnumSet.of(AiInputType.TEXT),
                128_000,
                16_384,
                AiCost.zero(),
                AiModelCompat.defaults());
    }

    private static final class FakeProvider implements AiProvider {
        private final String summary;
        private final List<AiProviderRequest> requests = new java.util.ArrayList<>();

        private FakeProvider(String summary) {
            this.summary = summary;
        }

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public String name() {
            return "Fake";
        }

        @Override
        public AiProviderApi api() {
            return AiProviderApi.CUSTOM;
        }

        @Override
        public List<AiModel> models() {
            return List.of(CodingSessionCompactorTest.model());
        }

        @Override
        public void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) {
            requests.add(request);
            sink.accept(new AiStreamEvent.MessageCompleted(
                    "summary-message",
                    new AiAssistantMessage(
                            List.of(new AiTextContent(summary)),
                            AiStopReason.STOP,
                            null)));
        }

        private String prompt() {
            com.agent4j.ai.AiUserMessage user =
                    (com.agent4j.ai.AiUserMessage) requests.getFirst().turn().messages().getFirst();
            return ((AiTextContent) user.content().getFirst()).text();
        }
    }
}
