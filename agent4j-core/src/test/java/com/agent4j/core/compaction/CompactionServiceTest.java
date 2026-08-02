package com.agent4j.core.compaction;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiCost;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelCompat;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderApi;
import com.agent4j.ai.AiProviderContext;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.ai.AiTextContent;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class CompactionServiceTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");
    private final AiModel model = model();

    @Test
    void compactsPlannedPrefixIntoSummaryMessageAndRetainedTail() throws Exception {
        FakeProvider provider = new FakeProvider("summary from model", true);
        CompactionService service = service();

        CompactionResult result = service.compact(
                request(
                        CompactionReason.MANUAL,
                        CompactionConfig.builder()
                                .keepTokens(0)
                                .keepMessages(1)
                                .summaryPrompt("Summarize:\n{messages}")
                                .build(),
                        "focus on files",
                        message("user-1", AgentMessageRole.USER, "Read README.md"),
                        message("assistant-1", AgentMessageRole.ASSISTANT, "README says hello")),
                provider,
                model,
                AiProviderContext.empty(),
                AiStreamOptions.defaults());

        assertThat(result.compacted()).isTrue();
        assertThat(result.retainedMessages()).extracting(AgentMessage::id)
                .containsExactly("assistant-1");
        assertThat(result.summaryMessage().role()).isEqualTo(AgentMessageRole.COMPACTION_SUMMARY);
        assertThat(result.summaryMessage().timestamp()).isEqualTo(NOW);
        assertThat(result.summaryMessage().parentId()).isEqualTo("user-1");
        assertThat(result.summaryMessage().textContent())
                .isEqualTo("Here is a summary of the conversation to date:\n\nsummary from model");
        assertThat(result.summaryMessage().metadata().path("reason").asText()).isEqualTo("manual");
        assertThat(result.summaryMessage().metadata().path("retainedEntries").get(0).asText())
                .isEqualTo("assistant-1");
        assertThat(result.compactedMessages()).extracting(AgentMessage::role)
                .containsExactly(AgentMessageRole.COMPACTION_SUMMARY, AgentMessageRole.ASSISTANT);
        assertThat(result.usageAfter().messageCount()).isEqualTo(2);
        assertThat(result.usageBefore().messageCount()).isEqualTo(2);
        assertThat(provider.requests).hasSize(1);
        assertThat(provider.prompt()).isEqualTo("""
                Summarize:
                Human: Read README.md

                <focusInstructions>
                focus on files
                </focusInstructions>""");
    }

    @Test
    void includesPriorCompactionSummaryInSummarizationPrompt() throws Exception {
        FakeProvider provider = new FakeProvider("new summary", true);

        service().compact(
                request(
                        CompactionReason.MANUAL,
                        CompactionConfig.builder()
                                .keepTokens(0)
                                .keepMessages(1)
                                .summaryPrompt("Summarize:\n{messages}")
                                .build(),
                        null,
                        message("summary-1", AgentMessageRole.COMPACTION_SUMMARY, "old summary"),
                        message("user-2", AgentMessageRole.USER, "continue")),
                provider,
                model,
                AiProviderContext.empty(),
                AiStreamOptions.defaults());

        assertThat(provider.prompt()).contains("Compaction Summary: old summary");
    }

    @Test
    void returnsNoOpWithoutCallingProviderWhenPlannerDoesNotCompact() throws Exception {
        FakeProvider provider = new FakeProvider("unreachable", true);

        CompactionResult result = service().compact(
                request(
                        CompactionReason.THRESHOLD,
                        CompactionConfig.builder()
                                .triggerMessages(10)
                                .triggerTokens(100_000)
                                .build(),
                        null,
                        message("user-1", AgentMessageRole.USER, "short")),
                provider,
                model,
                AiProviderContext.empty(),
                AiStreamOptions.defaults());

        assertThat(result.compacted()).isFalse();
        assertThat(provider.requests).isEmpty();
    }

    @Test
    void fallsBackToTextDeltasWhenNoCompletedMessageIsProvided() throws Exception {
        FakeProvider provider = new FakeProvider("delta summary", false);

        CompactionResult result = service().compact(
                request(
                        CompactionReason.MANUAL,
                        CompactionConfig.builder()
                                .keepTokens(0)
                                .keepMessages(1)
                                .build(),
                        null,
                        message("user-1", AgentMessageRole.USER, "old"),
                        message("assistant-1", AgentMessageRole.ASSISTANT, "new")),
                provider,
                model,
                AiProviderContext.empty(),
                AiStreamOptions.defaults());

        assertThat(result.summaryMessage().textContent()).contains("delta summary");
    }

    @Test
    void usesUnavailableSummaryMarkerWhenProviderReturnsNoText() throws Exception {
        FakeProvider provider = new FakeProvider("", false);

        CompactionResult result = service().compact(
                request(
                        CompactionReason.MANUAL,
                        CompactionConfig.builder()
                                .keepTokens(0)
                                .keepMessages(1)
                                .build(),
                        null,
                        message("user-1", AgentMessageRole.USER, "old"),
                        message("assistant-1", AgentMessageRole.ASSISTANT, "new")),
                provider,
                model,
                AiProviderContext.empty(),
                AiStreamOptions.defaults());

        assertThat(result.summaryMessage().textContent()).contains("(Summary unavailable)");
    }

    private static CompactionService service() {
        return new CompactionService(
                new CompactionPlanner(),
                new CompactionSerializer(),
                text -> text == null ? 0 : text.length(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CompactionRequest request(
            CompactionReason reason,
            CompactionConfig config,
            String focusInstructions,
            AgentMessage... messages
    ) {
        return new CompactionRequest(
                "session-1",
                reason,
                List.of(messages),
                "system prompt",
                config,
                focusInstructions);
    }

    private static AgentMessage message(String id, AgentMessageRole role, String text) {
        return new AgentMessage(
                id,
                null,
                NOW,
                role,
                ContentBlocks.toJsonArray(List.of(new TextBlock(text, null))),
                JSON.objectNode());
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
                EnumSet.of(com.agent4j.ai.AiInputType.TEXT),
                128_000,
                16_384,
                AiCost.zero(),
                AiModelCompat.defaults());
    }

    private static final class FakeProvider implements AiProvider {
        private final String summary;
        private final boolean completeMessage;
        private final List<AiProviderRequest> requests = new java.util.ArrayList<>();

        private FakeProvider(String summary, boolean completeMessage) {
            this.summary = summary;
            this.completeMessage = completeMessage;
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
            return List.of(CompactionServiceTest.model());
        }

        @Override
        public void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) {
            requests.add(request);
            sink.accept(new AiStreamEvent.TextDelta("summary-message", 0, summary));
            if (completeMessage) {
                sink.accept(new AiStreamEvent.MessageCompleted(
                        "summary-message",
                        new AiAssistantMessage(
                                List.of(new AiTextContent(summary)),
                                AiStopReason.STOP,
                                null)));
            }
        }

        private String prompt() {
            AiProviderRequest request = requests.getFirst();
            com.agent4j.ai.AiUserMessage user = (com.agent4j.ai.AiUserMessage) request.turn().messages().getFirst();
            return ((AiTextContent) user.content().getFirst()).text();
        }
    }
}
