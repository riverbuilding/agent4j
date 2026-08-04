package com.agent4j.core.compaction;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiCost;
import com.agent4j.ai.AiInputType;
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

class BranchSummaryServiceTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");

    @Test
    void generatesBranchSummaryMessageWithSourceMetadata() throws Exception {
        FakeProvider provider = new FakeProvider("branch kept README context");
        BranchSummaryService service = new BranchSummaryService(
                new CompactionSerializer(),
                text -> text == null ? 0 : text.length(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        BranchSummaryResult result = service.summarize(
                new BranchSummaryRequest(
                        "session-1",
                        List.of(
                                message("user-1", AgentMessageRole.USER, "Read README.md"),
                                message("assistant-1", AgentMessageRole.ASSISTANT, "README says hello")),
                        "system prompt",
                        "Branch summary:\n{messages}",
                        "keep file decisions",
                        "assistant-1",
                        "target-session"),
                provider,
                model(),
                AiProviderContext.empty(),
                AiStreamOptions.defaults());

        assertThat(provider.prompt()).isEqualTo("""
                Branch summary:
                Human: Read README.md

                AI: README says hello

                <focusInstructions>
                keep file decisions
                </focusInstructions>""");
        assertThat(result.summaryMessage().role()).isEqualTo(AgentMessageRole.BRANCH_SUMMARY);
        assertThat(result.summaryMessage().parentId()).isEqualTo("assistant-1");
        assertThat(result.summaryMessage().timestamp()).isEqualTo(NOW);
        assertThat(result.summaryMessage().textContent())
                .isEqualTo("Here is a summary of the source conversation branch:\n\nbranch kept README context");
        assertThat(result.summaryMessage().metadata().path("summaryKind").asText()).isEqualTo("branch");
        assertThat(result.summaryMessage().metadata().path("sourceEntryId").asText()).isEqualTo("assistant-1");
        assertThat(result.summaryMessage().metadata().path("targetSessionId").asText()).isEqualTo("target-session");
        assertThat(result.summaryMessage().metadata().path("sourceEntries").get(0).asText()).isEqualTo("user-1");
        assertThat(result.usageBefore().messageCount()).isEqualTo(2);
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
            return List.of(BranchSummaryServiceTest.model());
        }

        @Override
        public void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) {
            requests.add(request);
            sink.accept(new AiStreamEvent.MessageCompleted(
                    "branch-summary-message",
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
