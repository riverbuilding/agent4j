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
import com.agent4j.core.compaction.BranchSummaryResult;
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

class CodingBranchSummarizerTest {
    private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");

    @TempDir
    Path tempDir;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void summarizesSourceActivePathAndAppendsBranchSummaryToTargetSession() throws Exception {
        SessionManager source = sessionManager("source");
        source.appendUserMessage("Read README.md");
        source.appendAssistantText("README says hello");
        SessionManager target = source.forkToActivePath(tempDir.resolve("fork.jsonl"));
        FakeProvider provider = new FakeProvider("branch summary from model");

        BranchSummaryResult result = new CodingBranchSummarizer().summarizeAndAppend(
                new BranchSummaryGenerationRequest(
                        source,
                        target,
                        new AiProviderSelection(provider, model()),
                        AiResolvedAuth.none(),
                        tempDir,
                        "system prompt",
                        "Summarize branch:\n{messages}",
                        "preserve README outcome",
                        AiStreamOptions.defaults()));

        assertThat(result.summaryMessage().role().wireName()).isEqualTo("branchSummary");
        assertThat(provider.requests).hasSize(1);
        assertThat(provider.prompt()).isEqualTo("""
                Summarize branch:
                Human: Read README.md

                AI: README says hello

                <focusInstructions>
                preserve README outcome
                </focusInstructions>""");
        assertThat(provider.requests.getFirst().context().sessionId()).contains(sessionId(source));
        assertThat(provider.requests.getFirst().context().attributes().get("summaryKind")).isEqualTo("branch");
        assertThat(provider.requests.getFirst().context().attributes().get("targetSessionId")).isEqualTo(sessionId(target));

        assertThat(target.document().entries()).extracting(entry -> entry.type())
                .containsExactly(
                        SessionEntryType.MESSAGE,
                        SessionEntryType.MESSAGE,
                        SessionEntryType.MESSAGE);
        assertThat(target.document().entries().get(2).message().orElseThrow().role())
                .isEqualTo(SessionMessageRole.BRANCH_SUMMARY);
        assertThat(target.document().entries().get(2).message().orElseThrow().content().toString())
                .contains("branch summary from model");
        assertThat(target.document().entries().get(2).message().orElseThrow().payload().path("sourceEntryId").asText())
                .isEqualTo(source.activeEntryId());
        assertThat(Files.readAllLines(target.sessionFile())).hasSize(4);
    }

    private SessionManager sessionManager(String name) throws Exception {
        AtomicInteger ids = new AtomicInteger();
        return SessionManager.create(
                tempDir.resolve(name + ".jsonl"),
                tempDir,
                new SessionJsonlCodec(),
                () -> name + "-" + ids.incrementAndGet(),
                clock);
    }

    private static String sessionId(SessionManager manager) {
        return manager.document().header().header().orElseThrow().id();
    }

    private static AiModel model() {
        return new AiModel(
                new AiModelReference("fake", "branch-summary-model"),
                "Branch Summary Model",
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
            return List.of(CodingBranchSummarizerTest.model());
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
