package com.agent4j.testkit.ai;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderApi;
import com.agent4j.ai.AiProviderContext;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiToolCallContent;
import com.agent4j.ai.AiTurnRequest;
import com.agent4j.ai.AiUsage;
import com.agent4j.ai.AiUserMessage;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderContractTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void fakeProviderRecordsRequestsAndSatisfiesNormalizedStreamContract() throws Exception {
        AiModel model = new AiModel(new AiModelReference("fake", "model"), "Fake Model");
        FakeProvider provider = new FakeProvider("fake", "Fake", AiProviderApi.CUSTOM, List.of(model))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.TextStarted("assistant-1", 0),
                        new AiStreamEvent.TextDelta("assistant-1", 0, "ok"),
                        new AiStreamEvent.TextEnded("assistant-1", 0),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("ok")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))));
        AiProviderRequest request = request(model);

        AiProviderContractAssertions.assertNormalizedStreamContract(provider, request);

        assertThat(provider.requests()).containsExactly(request);
        assertThat(provider.model("model")).contains(model);
    }

    @Test
    void recordedProviderReplaysFixtureAsNormalizedStream() throws Exception {
        RecordedProviderFixture fixture = RecordedProviderFixture.read(Path.of(
                "src/test/resources/fixtures/ai/recorded-provider-text-toolcall.json"));
        RecordedProvider provider = new RecordedProvider(fixture);
        AiProviderRequest request = request(fixture.model());
        List<AiStreamEvent> events = new ArrayList<>();
        java.util.function.Consumer<AiStreamEvent> contract = AiProviderContractAssertions.assertNormalizedStreamContract();

        provider.stream(request, event -> {
            contract.accept(event);
            events.add(event);
        });

        assertThat(provider.id()).isEqualTo("recorded-openai");
        assertThat(provider.api()).isEqualTo(AiProviderApi.OPENAI_RESPONSES);
        assertThat(provider.models()).containsExactly(fixture.model());
        assertThat(provider.requests()).containsExactly(request);
        assertThat(events).hasSize(8);
        assertThat(events.get(5)).isInstanceOfSatisfying(AiStreamEvent.ToolCallDelta.class, event ->
                assertThat(event.delta().get("path").asText()).isEqualTo("README.md"));
        assertThat(events.getLast()).isInstanceOfSatisfying(AiStreamEvent.MessageCompleted.class, event -> {
            assertThat(event.message().stopReason()).isEqualTo(AiStopReason.TOOL_USE);
            assertThat(event.message().usage()).isEqualTo(new AiUsage(100, 12, 8, 3));
            assertThat(event.message().content()).containsExactly(
                    new AiTextContent("Reading"),
                    new AiToolCallContent("call-1", "read", JSON.objectNode().put("path", "README.md")));
        });
        AiProviderContractAssertions.assertNoEventsAfterTerminal(events);
    }

    private static AiProviderRequest request(AiModel model) {
        return new AiProviderRequest(
                model,
                new AiTurnRequest(List.of(AiUserMessage.text("hello")), List.of()),
                AiProviderContext.empty(),
                AiStreamOptions.defaults());
    }
}
