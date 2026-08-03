package com.agent4j.core.runtime;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderApi;
import com.agent4j.ai.AiResolvedAuth;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiThinkingContent;
import com.agent4j.ai.AiToolCallContent;
import com.agent4j.ai.AiToolResultMessage;
import com.agent4j.ai.AiUsage;
import com.agent4j.core.compaction.CompactionConfig;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.ToolContext;
import com.agent4j.core.tool.ToolExecutionHook;
import com.agent4j.core.tool.ToolRegistry;
import com.agent4j.core.tool.ToolSpec;
import com.agent4j.testkit.ai.FakeModelClient;
import com.agent4j.testkit.ai.FakeProvider;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentLoopTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void runsTextOnlyTurnAgainstFakeModel() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueue(List.of(
                new AiStreamEvent.MessageStarted("assistant-1"),
                new AiStreamEvent.TextDelta("assistant-1", 0, "hello"),
                new AiStreamEvent.MessageCompleted(
                        "assistant-1",
                        new AiAssistantMessage(
                                List.of(new AiTextContent("hello")),
                                AiStopReason.STOP,
                                new AiUsage(3, 2, 1, 0)))));
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        AgentLoopResult result = new AgentLoop(model, InMemoryToolRegistry.builder().build(), bus)
                .runTurn(request(List.of(userMessage("user-1", "say hi")), 2));

        assertThat(result.assistantMessages()).hasSize(1);
        assertThat(result.messages()).extracting(AgentMessage::id).containsExactly("user-1", "assistant-1");
        assertThat(result.assistantMessages().getFirst().textContent()).isEqualTo("hello");
        assertThat(result.toolResults()).isEmpty();
        assertThat(result.usage()).isEqualTo(new Usage(3, 2, 1, 0));
        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsExactly(
                        "AgentStarted",
                        "TurnStarted",
                        "MessageStarted",
                        "MessageEnded",
                        "MessageStarted",
                        "MessageUpdated",
                        "MessageEnded",
                        "TurnEnded",
                        "AgentEnded");
        assertThat(((AgentEvent.MessageStarted) events.get(2)).message().role()).isEqualTo(AgentMessageRole.USER);
        assertThat(((AgentEvent.MessageEnded) events.get(3)).message().role()).isEqualTo(AgentMessageRole.USER);
        assertThat(((AgentEvent.MessageStarted) events.get(4)).message().role()).isEqualTo(AgentMessageRole.ASSISTANT);
        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().getFirst().messages()).hasSize(1);
    }

    @Test
    void prependsSystemPromptToModelRequestWithoutPersistingItInTranscript() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueue(List.of(
                new AiStreamEvent.MessageStarted("assistant-1"),
                new AiStreamEvent.MessageCompleted(
                        "assistant-1",
                        new AiAssistantMessage(
                                List.of(new AiTextContent("hello")),
                                AiStopReason.STOP,
                                AiUsage.zero()))));

        AgentLoopResult result = new AgentLoop(model, InMemoryToolRegistry.builder().build(), new AgentEventBus())
                .runTurn(new AgentLoopRequest(
                        "session-1",
                        "turn-1",
                        "user-1",
                        List.of(userMessage("user-1", "say hi")),
                        Path.of("/repo"),
                        clock,
                        new AbortController().signal(),
                        Map.of(),
                        "Use concise answers.",
                        1));

        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().getFirst().messages()).extracting(AiMessage::role)
                .containsExactly("system", "user");
        assertThat(((com.agent4j.ai.AiSystemMessage) model.requests().getFirst().messages().getFirst()).content())
                .isEqualTo("Use concise answers.");
        assertThat(result.messages()).extracting(AgentMessage::role)
                .containsExactly(AgentMessageRole.USER, AgentMessageRole.ASSISTANT);
    }

    @Test
    void publishesPiStyleAssistantStreamFragmentsAsMessageUpdates() throws Exception {
        ToolCall toolCall = new ToolCall("tool-1", "echo", JSON.objectNode().put("text", "hello"));
        FakeModelClient model = new FakeModelClient().enqueue(List.of(
                new AiStreamEvent.MessageStarted("assistant-1"),
                new AiStreamEvent.TextStarted("assistant-1", 0),
                new AiStreamEvent.TextDelta("assistant-1", 0, "hello"),
                new AiStreamEvent.TextEnded("assistant-1", 0),
                new AiStreamEvent.ThinkingStarted("assistant-1", 1),
                new AiStreamEvent.ThinkingDelta("assistant-1", 1, "checking"),
                new AiStreamEvent.ThinkingEnded("assistant-1", 1),
                new AiStreamEvent.ToolCallStarted("assistant-1", 2, toolCall.id(), toolCall.name()),
                new AiStreamEvent.ToolCallDelta("assistant-1", 2, JSON.objectNode().put("text", "hello")),
                new AiStreamEvent.ToolCallEnded("assistant-1", 2, toolCall.id()),
                new AiStreamEvent.MessageCompleted(
                        "assistant-1",
                        new AiAssistantMessage(
                                List.of(new AiTextContent("hello"), new AiThinkingContent("checking")),
                                AiStopReason.STOP,
                                AiUsage.zero()))));
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        AgentLoopResult result = new AgentLoop(model, InMemoryToolRegistry.builder().build(), bus)
                .runTurn(request(List.of(userMessage("user-1", "stream")), 2));

        assertThat(result.assistantMessages()).extracting(AgentMessage::id).containsExactly("assistant-1");
        assertThat(events.stream()
                .filter(AgentEvent.MessageUpdated.class::isInstance)
                .map(AgentEvent.MessageUpdated.class::cast)
                .map(update -> update.delta().get("type").asText()))
                .containsExactly(
                        "text_start",
                        "text_delta",
                        "text_end",
                        "thinking_start",
                        "thinking_delta",
                        "thinking_end",
                        "toolcall_start",
                        "toolcall_delta",
                        "toolcall_end");
        AgentEvent.MessageUpdated toolStart = events.stream()
                .filter(AgentEvent.MessageUpdated.class::isInstance)
                .map(AgentEvent.MessageUpdated.class::cast)
                .filter(update -> update.delta().path("type").asText().equals("toolcall_start"))
                .findFirst()
                .orElseThrow();
        assertThat(toolStart.delta().path("toolCallId").asText()).isEqualTo("tool-1");
        assertThat(toolStart.delta().path("toolName").asText()).isEqualTo("echo");
    }

    @Test
    void executesToolCallsAndContinuesUntilTerminalMessage() throws Exception {
        ToolCall toolCall = new ToolCall("tool-1", "echo", JSON.objectNode().put("text", "hello"));
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                                        AiStopReason.TOOL_USE,
                                        new AiUsage(5, 1, 0, 0)))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("done")),
                                        AiStopReason.STOP,
                                        new AiUsage(4, 2, 0, 0)))));
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("echo", "Echo text", JSON.objectNode().put("type", "object")), (call, context) ->
                        new ToolResult(call.id(), call.name(), false, call.arguments().get("text"), JSON.objectNode()))
                .build();
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        AgentLoopResult result = new AgentLoop(model, registry, bus)
                .runTurn(request(List.of(userMessage("user-1", "echo hello")), 2));

        assertThat(result.assistantMessages()).extracting(AgentMessage::id)
                .containsExactly("assistant-1", "assistant-2");
        assertThat(result.messages()).extracting(AgentMessage::id)
                .containsExactly("user-1", "assistant-1", "tool-result-tool-1", "assistant-2");
        assertThat(result.messages()).extracting(AgentMessage::role)
                .containsExactly(
                        AgentMessageRole.USER,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.TOOL_RESULT,
                        AgentMessageRole.ASSISTANT);
        assertThat(result.assistantMessages().getLast().textContent()).isEqualTo("done");
        assertThat(result.toolResults()).hasSize(1);
        assertThat(result.toolResults().getFirst().content().asText()).isEqualTo("hello");
        assertThat(result.usage()).isEqualTo(new Usage(9, 3, 0, 0));
        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsExactly(
                        "AgentStarted",
                        "TurnStarted",
                        "MessageStarted",
                        "MessageEnded",
                        "MessageStarted",
                        "MessageEnded",
                        "ToolExecutionStarted",
                        "ToolExecutionEnded",
                        "MessageStarted",
                        "MessageEnded",
                        "TurnEnded",
                        "TurnStarted",
                        "MessageStarted",
                        "MessageEnded",
                        "TurnEnded",
                        "AgentEnded");
        assertThat(((AgentEvent.MessageStarted) events.get(2)).message().role()).isEqualTo(AgentMessageRole.USER);
        assertThat(((AgentEvent.MessageEnded) events.get(3)).message().role()).isEqualTo(AgentMessageRole.USER);
        assertThat(((AgentEvent.MessageStarted) events.get(4)).message().role()).isEqualTo(AgentMessageRole.ASSISTANT);
        assertThat(((AgentEvent.MessageStarted) events.get(8)).message().role()).isEqualTo(AgentMessageRole.TOOL_RESULT);
        assertThat(((AgentEvent.MessageEnded) events.get(9)).message().role()).isEqualTo(AgentMessageRole.TOOL_RESULT);
        AgentEvent.TurnEnded firstTurnEnd = (AgentEvent.TurnEnded) events.get(10);
        assertThat(firstTurnEnd.message().id()).isEqualTo("assistant-1");
        assertThat(firstTurnEnd.toolResults()).extracting(AgentMessage::role)
                .containsExactly(AgentMessageRole.TOOL_RESULT);
        assertThat(firstTurnEnd.toolResults().getFirst().id()).isEqualTo("tool-result-tool-1");
        AgentEvent.TurnEnded secondTurnEnd = (AgentEvent.TurnEnded) events.get(14);
        assertThat(secondTurnEnd.message().id()).isEqualTo("assistant-2");
        assertThat(secondTurnEnd.toolResults()).isEmpty();
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().getLast().role()).isEqualTo("toolResult");
        assertThat(((com.agent4j.ai.AiToolResultMessage) model.requests().get(1).messages().getLast())
                .content().getFirst()).isEqualTo(new AiTextContent("hello"));
    }

    @Test
    void providerBackedLoopNormalizesUsageToolCallsAndToolResults() throws Exception {
        AiModel model = new AiModel(new AiModelReference("fake-provider", "fake-model"), "Fake Model");
        ToolCall toolCall = new ToolCall("tool-1", "echo", JSON.objectNode().put("text", "hello"));
        FakeProvider provider = new FakeProvider(
                "fake-provider",
                "Fake Provider",
                AiProviderApi.CUSTOM,
                List.of(model))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                                        AiStopReason.TOOL_USE,
                                        new AiUsage(5, 1, 2, 0)))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("done")),
                                        AiStopReason.STOP,
                                        new AiUsage(4, 2, 0, 1)))));
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("echo", "Echo text", JSON.objectNode().put("type", "object")), (call, context) ->
                        new ToolResult(call.id(), call.name(), false, call.arguments().get("text"), JSON.objectNode()))
                .build();
        AgentMessage prompt = userMessage("user-1", "echo hello");
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);
        AgentLoopRequest request = new AgentLoopRequest(
                "session-1",
                "turn-1",
                "user-1",
                List.of(prompt),
                Path.of("/repo"),
                clock,
                new AbortController().signal(),
                Map.of(),
                null,
                2,
                3,
                Optional.of(Duration.ofSeconds(30)),
                ToolExecutionMode.PARALLEL,
                List.of(prompt),
                List.of(),
                List.of(),
                QueueMode.ONE_AT_A_TIME,
                QueueMode.ONE_AT_A_TIME);

        AgentLoopResult result = new AgentLoop(provider, model, registry, bus)
                .runTurn(request);

        assertThat(provider.requests()).hasSize(2);
        assertThat(provider.requests().getFirst().model()).isEqualTo(model);
        assertThat(provider.requests().getFirst().context().sessionId()).contains("session-1");
        assertThat(provider.requests().getFirst().context().turnId()).contains("turn-1");
        assertThat(provider.requests().getFirst().context().cwd()).contains(Path.of("/repo").toAbsolutePath().normalize());
        assertThat(provider.requests().getFirst().options().timeout()).contains(Duration.ofSeconds(30));
        assertThat(provider.requests().getFirst().options().maxRetries()).isEqualTo(3);
        assertThat(provider.requests().getFirst().turn().tools()).extracting(tool -> tool.name()).containsExactly("echo");
        assertThat(provider.requests().get(1).turn().messages()).extracting(AiMessage::role)
                .containsExactly("user", "assistant", "toolResult");
        assertThat(provider.requests().get(1).turn().messages().getLast())
                .isInstanceOfSatisfying(AiToolResultMessage.class, toolResult -> {
                    assertThat(toolResult.toolCallId()).isEqualTo("tool-1");
                    assertThat(toolResult.toolName()).isEqualTo("echo");
                    assertThat(toolResult.content()).containsExactly(new AiTextContent("hello"));
                    assertThat(toolResult.error()).isFalse();
                });
        assertThat(result.messages()).extracting(AgentMessage::id)
                .containsExactly("user-1", "assistant-1", "tool-result-tool-1", "assistant-2");
        assertThat(result.toolResults()).extracting(ToolResult::toolCallId).containsExactly("tool-1");
        assertThat(result.usage()).isEqualTo(new Usage(9, 3, 2, 1));
        assertThat(events.stream()
                .filter(AgentEvent.TurnEnded.class::isInstance)
                .map(AgentEvent.TurnEnded.class::cast)
                .map(AgentEvent.TurnEnded::usage))
                .containsExactly(new Usage(5, 1, 2, 0), new Usage(4, 2, 0, 1));
    }

    @Test
    void providerBackedLoopCarriesResolvedAuthIntoProviderContext() throws Exception {
        AiModel model = new AiModel(new AiModelReference("fake-provider", "fake-model"), "Fake Model");
        FakeProvider provider = new FakeProvider(
                "fake-provider",
                "Fake Provider",
                AiProviderApi.CUSTOM,
                List.of(model))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("done")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))));
        AiResolvedAuth auth = new AiResolvedAuth(
                Optional.of("sk-test"),
                Map.of("X-Test", "yes"),
                Optional.of("https://provider.test"),
                Optional.of("test"),
                Map.of());

        new AgentLoop(provider, model, auth, InMemoryToolRegistry.builder().build(), new AgentEventBus())
                .runTurn(request(List.of(userMessage("user-1", "hello")), 1));

        assertThat(provider.requests()).hasSize(1);
        assertThat(provider.requests().getFirst().context().auth()).isEqualTo(auth);
    }

    @Test
    void thresholdCompactionRunsBeforeProviderModelRoundAndRebuildsModelMessages() throws Exception {
        AiModel model = new AiModel(new AiModelReference("fake-provider", "fake-model"), "Fake Model");
        FakeProvider provider = new FakeProvider(
                "fake-provider",
                "Fake Provider",
                AiProviderApi.CUSTOM,
                List.of(model))
                .enqueue(List.of(
                        new AiStreamEvent.MessageCompleted(
                                "compaction-model-message",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("old work summarized")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("done")),
                                        AiStopReason.STOP,
                                        new AiUsage(4, 2, 0, 0)))));
        AgentMessage oldUser = userMessage("user-1", "old request with lots of detail");
        AgentMessage oldAssistant = customMessage("assistant-1", AgentMessageRole.ASSISTANT, "old assistant answer");
        AgentMessage prompt = userMessage("user-2", "continue");
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        AgentLoopResult result = new AgentLoop(provider, model, InMemoryToolRegistry.builder().build(), bus)
                .runTurn(new AgentLoopRequest(
                        "session-1",
                        "turn-1",
                        prompt.id(),
                        List.of(oldUser, oldAssistant, prompt),
                        Path.of("/repo"),
                        clock,
                        new AbortController().signal(),
                        Map.of(),
                        "Use concise answers.",
                        1,
                        0,
                        Optional.empty(),
                        ToolExecutionMode.PARALLEL,
                        List.of(prompt),
                        List.of(),
                        List.of(),
                        QueueMode.ONE_AT_A_TIME,
                        QueueMode.ONE_AT_A_TIME,
                        CompactionConfig.builder()
                                .triggerMessages(2)
                                .keepTokens(0)
                                .keepMessages(1)
                                .summaryPrompt("Summarize:\n{messages}")
                                .build()));

        assertThat(provider.requests()).hasSize(2);
        assertThat(provider.requests().getFirst().turn().tools()).isEmpty();
        assertThat(provider.requests().getFirst().turn().messages()).extracting(AiMessage::role)
                .containsExactly("user");
        assertThat(((com.agent4j.ai.AiUserMessage) provider.requests().getFirst().turn().messages().getFirst())
                .content().getFirst())
                .isInstanceOfSatisfying(AiTextContent.class, text ->
                        assertThat(text.text()).isEqualTo("""
                                Summarize:
                                Human: old request with lots of detail

                                AI: old assistant answer"""));
        assertThat(provider.requests().get(1).turn().messages()).extracting(AiMessage::role)
                .containsExactly("system", "user", "user");
        assertThat(result.messages()).extracting(AgentMessage::role)
                .containsExactly(
                        AgentMessageRole.USER,
                        AgentMessageRole.COMPACTION_SUMMARY,
                        AgentMessageRole.ASSISTANT);
        assertThat(result.messages().get(1).textContent()).contains("old work summarized");
        assertThat(events.stream().filter(AgentEvent.CompactionStarted.class::isInstance))
                .hasSize(1);
        assertThat(events.stream()
                .filter(AgentEvent.CompactionCompleted.class::isInstance)
                .map(AgentEvent.CompactionCompleted.class::cast)
                .map(AgentEvent.CompactionCompleted::summaryMessageId))
                .singleElement()
                .isEqualTo(result.messages().get(1).id());
    }

    @Test
    void thresholdCompactionCanChainWhenTranscriptGrowsAgainInSameRun() throws Exception {
        AiModel model = new AiModel(new AiModelReference("fake-provider", "fake-model"), "Fake Model");
        ToolCall toolCall = new ToolCall("tool-1", "echo", JSON.objectNode().put("text", "hello"));
        FakeProvider provider = new FakeProvider(
                "fake-provider",
                "Fake Provider",
                AiProviderApi.CUSTOM,
                List.of(model))
                .enqueue(List.of(
                        new AiStreamEvent.MessageCompleted(
                                "compaction-1",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("ROUND_1")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                                        AiStopReason.TOOL_USE,
                                        AiUsage.zero()))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageCompleted(
                                "compaction-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("ROUND_2")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("done")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))));
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("echo", "Echo text", JSON.objectNode().put("type", "object")), (call, context) ->
                        new ToolResult(call.id(), call.name(), false, call.arguments().get("text"), JSON.objectNode()))
                .build();
        AgentMessage oldUser = userMessage("user-1", "old request");
        AgentMessage oldAssistant = customMessage("assistant-0", AgentMessageRole.ASSISTANT, "old answer");
        AgentMessage prompt = userMessage("user-2", "continue");
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        AgentLoopResult result = new AgentLoop(provider, model, registry, bus)
                .runTurn(new AgentLoopRequest(
                        "session-1",
                        "turn-1",
                        prompt.id(),
                        List.of(oldUser, oldAssistant, prompt),
                        Path.of("/repo"),
                        clock,
                        new AbortController().signal(),
                        Map.of(),
                        null,
                        2,
                        0,
                        Optional.empty(),
                        ToolExecutionMode.SEQUENTIAL,
                        List.of(prompt),
                        List.of(),
                        List.of(),
                        QueueMode.ONE_AT_A_TIME,
                        QueueMode.ONE_AT_A_TIME,
                        CompactionConfig.builder()
                                .triggerMessages(2)
                                .keepTokens(0)
                                .keepMessages(2)
                                .summaryPrompt("Summarize:\n{messages}")
                                .build()));

        assertThat(provider.requests()).hasSize(4);
        assertThat(((com.agent4j.ai.AiUserMessage) provider.requests().get(2).turn().messages().getFirst())
                .content().getFirst())
                .isInstanceOfSatisfying(AiTextContent.class, text -> {
                    assertThat(text.text()).contains("ROUND_1");
                    assertThat(text.text()).contains("Human: continue");
                });
        assertThat(provider.requests().get(3).turn().messages()).extracting(AiMessage::role)
                .containsExactly("user", "assistant", "toolResult");
        assertThat(events.stream().filter(AgentEvent.CompactionStarted.class::isInstance))
                .hasSize(2);
        assertThat(result.messages()).extracting(AgentMessage::role)
                .containsExactly(
                        AgentMessageRole.USER,
                        AgentMessageRole.COMPACTION_SUMMARY,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.TOOL_RESULT,
                        AgentMessageRole.COMPACTION_SUMMARY,
                        AgentMessageRole.ASSISTANT);
        assertThat(result.messages().stream()
                .filter(message -> message.role() == AgentMessageRole.COMPACTION_SUMMARY)
                .map(AgentMessage::textContent))
                .anySatisfy(text -> assertThat(text).contains("ROUND_1"))
                .anySatisfy(text -> assertThat(text).contains("ROUND_2"));
    }

    @Test
    void rejectsNonPositiveModelTimeout() {
        AgentMessage user = userMessage("user-1", "hello");

        assertThatThrownBy(() -> new AgentLoopRequest(
                "session-1",
                "turn-1",
                user.id(),
                List.of(user),
                Path.of("/repo"),
                clock,
                new AbortController().signal(),
                Map.of(),
                null,
                1,
                0,
                Optional.of(Duration.ZERO),
                List.of(user),
                List.of(),
                List.of(),
                QueueMode.ONE_AT_A_TIME,
                QueueMode.ONE_AT_A_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelTimeout");
    }

    @Test
    void emitsMultiToolResultsInAssistantSourceOrder() throws Exception {
        ToolCall first = new ToolCall("tool-1", "echo", JSON.objectNode().put("text", "first"));
        ToolCall second = new ToolCall("tool-2", "echo", JSON.objectNode().put("text", "second"));
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(
                                                new AiToolCallContent(first.id(), first.name(), first.arguments()),
                                                new AiToolCallContent(second.id(), second.name(), second.arguments())),
                                        AiStopReason.TOOL_USE,
                                        AiUsage.zero()))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("done")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))));
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("echo", "Echo text", JSON.objectNode().put("type", "object")), (call, context) ->
                        new ToolResult(call.id(), call.name(), false, call.arguments().get("text"), JSON.objectNode()))
                .build();
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        AgentLoopResult result = new AgentLoop(model, registry, bus)
                .runTurn(request(List.of(userMessage("user-1", "echo twice")), 2));

        assertThat(result.messages()).extracting(AgentMessage::id)
                .containsExactly(
                        "user-1",
                        "assistant-1",
                        "tool-result-tool-1",
                        "tool-result-tool-2",
                        "assistant-2");
        assertThat(result.toolResults()).extracting(ToolResult::toolCallId)
                .containsExactly("tool-1", "tool-2");
        AgentEvent.TurnEnded firstTurnEnd = events.stream()
                .filter(AgentEvent.TurnEnded.class::isInstance)
                .map(AgentEvent.TurnEnded.class::cast)
                .filter(event -> event.message().id().equals("assistant-1"))
                .findFirst()
                .orElseThrow();
        assertThat(firstTurnEnd.toolResults()).extracting(AgentMessage::id)
                .containsExactly("tool-result-tool-1", "tool-result-tool-2");
        assertThat(model.requests().get(1).messages()).extracting(AiMessage::role)
                .containsExactly("user", "assistant", "toolResult", "toolResult");
    }

    @Test
    void publishesToolExecutionUpdatesFromToolContext() throws Exception {
        ToolCall toolCall = new ToolCall("tool-1", "progress", JSON.objectNode());
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                                        AiStopReason.TOOL_USE,
                                        AiUsage.zero()))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("done")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))));
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("progress", "Publishes progress", JSON.objectNode()), (call, context) -> {
                    context.publishUpdate(JSON.objectNode().put("status", "running"));
                    return new ToolResult(call.id(), call.name(), false, JSON.textNode("ok"), JSON.objectNode());
                })
                .build();
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        new AgentLoop(model, registry, bus)
                .runTurn(request(List.of(userMessage("user-1", "run progress")), 2));

        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsSubsequence(
                        "ToolExecutionStarted",
                        "ToolExecutionUpdated",
                        "ToolExecutionEnded");
        AgentEvent.ToolExecutionUpdated update = events.stream()
                .filter(AgentEvent.ToolExecutionUpdated.class::isInstance)
                .map(AgentEvent.ToolExecutionUpdated.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(update.toolCallId()).isEqualTo("tool-1");
        assertThat(update.delta().path("status").asText()).isEqualTo("running");
    }

    @Test
    void runsBeforeAndAfterToolHooksAroundToolExecution() throws Exception {
        ToolCall toolCall = new ToolCall("tool-1", "hooked", JSON.objectNode());
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                                        AiStopReason.TOOL_USE,
                                        AiUsage.zero()))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("done")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))));
        List<String> observations = new ArrayList<>();
        ToolExecutionHook hook = new ToolExecutionHook() {
            @Override
            public Optional<ToolResult> beforeToolExecution(ToolCall toolCall, ToolContext context) {
                observations.add("before:" + toolCall.id());
                context.publishUpdate(JSON.objectNode().put("hook", "before"));
                return Optional.empty();
            }

            @Override
            public void afterToolExecution(ToolCall toolCall, ToolContext context, ToolResult result) {
                observations.add("after:" + result.toolCallId() + ":" + result.content().asText());
                context.publishUpdate(JSON.objectNode().put("hook", "after"));
            }
        };
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("hooked", "Hooked", JSON.objectNode()), (call, context) -> {
                    observations.add("execute:" + call.id());
                    context.publishUpdate(JSON.objectNode().put("tool", "running"));
                    return new ToolResult(call.id(), call.name(), false, JSON.textNode("ok"), JSON.objectNode());
                })
                .build();
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        new AgentLoop(model, registry, bus, List.of(hook))
                .runTurn(request(List.of(userMessage("user-1", "run hooked")), 2));

        assertThat(observations).containsExactly("before:tool-1", "execute:tool-1", "after:tool-1:ok");
        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsSubsequence(
                        "ToolExecutionStarted",
                        "ToolExecutionUpdated",
                        "ToolExecutionUpdated",
                        "ToolExecutionUpdated",
                        "ToolExecutionEnded");
        assertThat(events.stream()
                .filter(AgentEvent.ToolExecutionUpdated.class::isInstance)
                .map(AgentEvent.ToolExecutionUpdated.class::cast)
                .map(update -> update.delta().fieldNames().next()))
                .containsExactly("hook", "tool", "hook");
    }

    @Test
    void hookCanBlockToolExecutionWithStableResult() throws Exception {
        ToolCall toolCall = new ToolCall("tool-1", "blocked", JSON.objectNode());
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                                        AiStopReason.TOOL_USE,
                                        AiUsage.zero()))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("done")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))));
        AtomicBoolean executed = new AtomicBoolean(false);
        List<ToolResult> afterResults = new ArrayList<>();
        ToolExecutionHook hook = new ToolExecutionHook() {
            @Override
            public Optional<ToolResult> beforeToolExecution(ToolCall toolCall, ToolContext context) {
                context.publishUpdate(JSON.objectNode().put("status", "blocked"));
                return Optional.of(ToolResult.blocked(toolCall, "blocked by policy"));
            }

            @Override
            public void afterToolExecution(ToolCall toolCall, ToolContext context, ToolResult result) {
                afterResults.add(result);
            }
        };
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("blocked", "Should not run", JSON.objectNode()), (call, context) -> {
                    executed.set(true);
                    return new ToolResult(call.id(), call.name(), false, JSON.textNode("unreachable"), JSON.objectNode());
                })
                .build();
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        AgentLoopResult result = new AgentLoop(model, registry, bus, List.of(hook))
                .runTurn(request(List.of(userMessage("user-1", "run blocked")), 2));

        assertThat(executed).isFalse();
        assertThat(afterResults).hasSize(1);
        assertThat(afterResults.getFirst().error()).isTrue();
        assertThat(afterResults.getFirst().metadata().path("blocked").asBoolean()).isTrue();
        assertThat(result.toolResults()).hasSize(1);
        assertThat(result.toolResults().getFirst().content().asText()).isEqualTo("blocked by policy");
        assertThat(result.toolResults().getFirst().metadata().path("blocked").asBoolean()).isTrue();
        assertThat(result.messages()).extracting(AgentMessage::id)
                .containsExactly("user-1", "assistant-1", "tool-result-tool-1", "assistant-2");
        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsSubsequence(
                        "ToolExecutionStarted",
                        "ToolExecutionUpdated",
                        "ToolExecutionEnded",
                        "MessageStarted",
                        "MessageEnded");
        AgentEvent.ToolExecutionEnded ended = events.stream()
                .filter(AgentEvent.ToolExecutionEnded.class::isInstance)
                .map(AgentEvent.ToolExecutionEnded.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(ended.result().metadata().path("blocked").asBoolean()).isTrue();
    }

    @Test
    void terminatingToolResultEndsAgentWithoutFollowUpModelRound() throws Exception {
        ToolCall toolCall = new ToolCall("tool-1", "finish", JSON.objectNode());
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                                        AiStopReason.TOOL_USE,
                                        AiUsage.zero()))));
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("finish", "Finish", JSON.objectNode()), (call, context) ->
                        ToolResult.terminate(call, JSON.textNode("finished"), "task complete"))
                .build();
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        AgentLoopResult result = new AgentLoop(model, registry, bus)
                .runTurn(request(List.of(userMessage("user-1", "finish")), 2));

        assertThat(model.requests()).hasSize(1);
        assertThat(result.messages()).extracting(AgentMessage::id)
                .containsExactly("user-1", "assistant-1", "tool-result-tool-1");
        assertThat(result.toolResults()).hasSize(1);
        assertThat(result.toolResults().getFirst().terminate()).isTrue();
        assertThat(result.toolResults().getFirst().metadata().path("terminateReason").asText()).isEqualTo("task complete");
        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsExactly(
                        "AgentStarted",
                        "TurnStarted",
                        "MessageStarted",
                        "MessageEnded",
                        "MessageStarted",
                        "MessageEnded",
                        "ToolExecutionStarted",
                        "ToolExecutionEnded",
                        "MessageStarted",
                        "MessageEnded",
                        "TurnEnded",
                        "AgentEnded");
        AgentEvent.TurnEnded turnEnded = (AgentEvent.TurnEnded) events.get(10);
        assertThat(turnEnded.message().id()).isEqualTo("assistant-1");
        assertThat(turnEnded.toolResults()).extracting(AgentMessage::id)
                .containsExactly("tool-result-tool-1");
    }

    @Test
    void runsMultipleToolsInParallelByDefaultButEmitsResultsInSourceOrder() throws Exception {
        ToolCall first = new ToolCall("tool-1", "first", JSON.objectNode());
        ToolCall second = new ToolCall("tool-2", "second", JSON.objectNode());
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(
                                                new AiToolCallContent(first.id(), first.name(), first.arguments()),
                                                new AiToolCallContent(second.id(), second.name(), second.arguments())),
                                        AiStopReason.TOOL_USE,
                                        AiUsage.zero()))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("done")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))));
        CountDownLatch secondToolEntered = new CountDownLatch(1);
        AtomicBoolean firstObservedSecondTool = new AtomicBoolean(false);
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("first", "First", JSON.objectNode()), (call, context) -> {
                    firstObservedSecondTool.set(secondToolEntered.await(1, TimeUnit.SECONDS));
                    return new ToolResult(call.id(), call.name(), false, JSON.textNode("first"), JSON.objectNode());
                })
                .register(new ToolSpec("second", "Second", JSON.objectNode()), (call, context) -> {
                    secondToolEntered.countDown();
                    return new ToolResult(call.id(), call.name(), false, JSON.textNode("second"), JSON.objectNode());
                })
                .build();
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        AgentLoopResult result = new AgentLoop(model, registry, bus)
                .runTurn(request(List.of(userMessage("user-1", "run both")), 2));

        assertThat(firstObservedSecondTool).isTrue();
        assertThat(result.messages()).extracting(AgentMessage::id)
                .containsExactly(
                        "user-1",
                        "assistant-1",
                        "tool-result-tool-1",
                        "tool-result-tool-2",
                        "assistant-2");
        assertThat(result.toolResults()).extracting(ToolResult::toolCallId)
                .containsExactly("tool-1", "tool-2");
        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsSubsequence(
                        "ToolExecutionStarted",
                        "ToolExecutionStarted",
                        "ToolExecutionEnded",
                        "MessageStarted",
                        "MessageEnded",
                        "ToolExecutionEnded",
                        "MessageStarted",
                        "MessageEnded");
    }

    @Test
    void canRunMultipleToolsSequentiallyWhenRequested() throws Exception {
        ToolCall first = new ToolCall("tool-1", "record", JSON.objectNode().put("value", "first"));
        ToolCall second = new ToolCall("tool-2", "record", JSON.objectNode().put("value", "second"));
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(
                                                new AiToolCallContent(first.id(), first.name(), first.arguments()),
                                                new AiToolCallContent(second.id(), second.name(), second.arguments())),
                                        AiStopReason.TOOL_USE,
                                        AiUsage.zero()))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("done")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))));
        List<String> executionOrder = new ArrayList<>();
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("record", "Record", JSON.objectNode()), (call, context) -> {
                    executionOrder.add(call.arguments().path("value").asText());
                    return new ToolResult(call.id(), call.name(), false, JSON.textNode(call.arguments().path("value").asText()), JSON.objectNode());
                })
                .build();

        AgentLoopResult result = new AgentLoop(model, registry, new AgentEventBus())
                .runTurn(request(List.of(userMessage("user-1", "run both")), 2, ToolExecutionMode.SEQUENTIAL));

        assertThat(executionOrder).containsExactly("first", "second");
        assertThat(result.toolResults()).extracting(ToolResult::toolCallId)
                .containsExactly("tool-1", "tool-2");
    }

    @Test
    void injectsSteeringMessagesAfterCompletedTurnBeforeContinuing() throws Exception {
        ToolCall toolCall = new ToolCall("tool-1", "echo", JSON.objectNode().put("text", "hello"));
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                                        AiStopReason.TOOL_USE,
                                        AiUsage.zero()))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("steered")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))));
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("echo", "Echo text", JSON.objectNode().put("type", "object")), (call, context) ->
                        new ToolResult(call.id(), call.name(), false, call.arguments().get("text"), JSON.objectNode()))
                .build();
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);
        AgentMessage prompt = userMessage("user-1", "echo hello");
        AgentMessage steering = userMessage("steer-1", "change direction");

        AgentLoopResult result = new AgentLoop(model, registry, bus)
                .runTurn(request(List.of(prompt), 3, List.of(prompt), List.of(steering), List.of()));

        assertThat(result.messages()).extracting(AgentMessage::id)
                .containsExactly("user-1", "assistant-1", "tool-result-tool-1", "steer-1", "assistant-2");
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages()).extracting(AiMessage::role)
                .containsExactly("user", "assistant", "toolResult", "user");
        assertThat(events).anySatisfy(event -> {
            assertThat(event).isInstanceOf(AgentEvent.QueueUpdated.class);
            AgentEvent.QueueUpdated queueUpdated = (AgentEvent.QueueUpdated) event;
            assertThat(queueUpdated.queueKind()).isEqualTo(QueueKind.STEER);
            assertThat(queueUpdated.size()).isZero();
        });
    }

    @Test
    void drainsAllSteeringMessagesWhenQueueModeAllIsRequested() throws Exception {
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("first")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("second")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))));
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);
        AgentMessage prompt = userMessage("user-1", "start");
        AgentMessage firstSteering = userMessage("steer-1", "first steer");
        AgentMessage secondSteering = userMessage("steer-2", "second steer");

        AgentLoopResult result = new AgentLoop(model, InMemoryToolRegistry.builder().build(), bus)
                .runTurn(request(
                        List.of(prompt),
                        3,
                        List.of(prompt),
                        List.of(firstSteering, secondSteering),
                        List.of(),
                        QueueMode.ALL,
                        QueueMode.ONE_AT_A_TIME));

        assertThat(result.messages()).extracting(AgentMessage::id)
                .containsExactly("user-1", "assistant-1", "steer-1", "steer-2", "assistant-2");
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages()).extracting(AiMessage::role)
                .containsExactly("user", "assistant", "user", "user");
        assertThat(events.stream()
                .filter(AgentEvent.QueueUpdated.class::isInstance)
                .map(AgentEvent.QueueUpdated.class::cast))
                .singleElement()
                .satisfies(queueUpdated -> {
                    assertThat(queueUpdated.queueKind()).isEqualTo(QueueKind.STEER);
                    assertThat(queueUpdated.size()).isZero();
                });
    }

    @Test
    void injectsFollowUpMessagesOnlyAfterAgentWouldOtherwiseStop() throws Exception {
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("first")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-2"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-2",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("second")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))));
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);
        AgentMessage prompt = userMessage("user-1", "start");
        AgentMessage followUp = userMessage("follow-1", "also do this");

        AgentLoopResult result = new AgentLoop(model, InMemoryToolRegistry.builder().build(), bus)
                .runTurn(request(List.of(prompt), 3, List.of(prompt), List.of(), List.of(followUp)));

        assertThat(result.messages()).extracting(AgentMessage::id)
                .containsExactly("user-1", "assistant-1", "follow-1", "assistant-2");
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages()).extracting(AiMessage::role)
                .containsExactly("user", "assistant", "user");
        assertThat(events).anySatisfy(event -> {
            assertThat(event).isInstanceOf(AgentEvent.QueueUpdated.class);
            AgentEvent.QueueUpdated queueUpdated = (AgentEvent.QueueUpdated) event;
            assertThat(queueUpdated.queueKind()).isEqualTo(QueueKind.FOLLOW_UP);
            assertThat(queueUpdated.size()).isZero();
        });
    }

    @Test
    void retriesModelRoundFailureWithinConfiguredLimit() throws Exception {
        FakeModelClient model = new FakeModelClient()
                .enqueueFailure(new IllegalStateException("temporary provider failure"))
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiTextContent("recovered")),
                                        AiStopReason.STOP,
                                        AiUsage.zero()))));
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        AgentLoopResult result = new AgentLoop(model, InMemoryToolRegistry.builder().build(), bus)
                .runTurn(request(List.of(userMessage("user-1", "try")), 2, 1));

        assertThat(result.assistantMessages().getFirst().textContent()).isEqualTo("recovered");
        assertThat(model.requests()).hasSize(2);
        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .contains("RetryStarted", "RetryCompleted", "AgentEnded");
        AgentEvent.RetryStarted started = events.stream()
                .filter(AgentEvent.RetryStarted.class::isInstance)
                .map(AgentEvent.RetryStarted.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(started.attempt()).isEqualTo(1);
        assertThat(started.reason()).isEqualTo("temporary provider failure");
        AgentEvent.RetryCompleted completed = events.stream()
                .filter(AgentEvent.RetryCompleted.class::isInstance)
                .map(AgentEvent.RetryCompleted.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(completed.attempt()).isEqualTo(1);
        assertThat(completed.success()).isTrue();
    }

    @Test
    void emitsFailedRetryCompletionWhenRetryLimitIsExhausted() {
        FakeModelClient model = new FakeModelClient()
                .enqueueFailure(new IllegalStateException("first provider failure"))
                .enqueueFailure(new IllegalStateException("second provider failure"));
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        assertThatThrownBy(() -> new AgentLoop(model, InMemoryToolRegistry.builder().build(), bus)
                .runTurn(request(List.of(userMessage("user-1", "try")), 2, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("second provider failure");

        assertThat(model.requests()).hasSize(2);
        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsSubsequence("RetryStarted", "RetryCompleted");
        assertThat(events).noneMatch(AgentEvent.AgentEnded.class::isInstance);
        AgentEvent.RetryCompleted completed = events.stream()
                .filter(AgentEvent.RetryCompleted.class::isInstance)
                .map(AgentEvent.RetryCompleted.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(completed.attempt()).isEqualTo(1);
        assertThat(completed.success()).isFalse();
    }

    @Test
    void emitsAgentAbortedWhenModelStreamObservesAbortSignal() {
        AbortController controller = new AbortController();
        FakeModelClient model = new FakeModelClient().enqueue(List.of(
                new AiStreamEvent.MessageStarted("assistant-1"),
                new AiStreamEvent.TextDelta("assistant-1", 0, "partial"),
                new AiStreamEvent.TextDelta("assistant-1", 0, "ignored"),
                new AiStreamEvent.MessageCompleted(
                        "assistant-1",
                        new AiAssistantMessage(
                                List.of(new AiTextContent("ignored")),
                                AiStopReason.STOP,
                                AiUsage.zero()))));
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(event -> {
            events.add(event);
            if (event instanceof AgentEvent.MessageUpdated) {
                controller.abort("stop model");
            }
        });

        assertThatThrownBy(() -> new AgentLoop(model, InMemoryToolRegistry.builder().build(), bus)
                .runTurn(request(List.of(userMessage("user-1", "try")), 2, controller.signal())))
                .isInstanceOf(AgentAbortException.class)
                .hasMessage("stop model");
        assertThat(events.getLast()).isInstanceOf(AgentEvent.AgentAborted.class);
        assertThat(((AgentEvent.AgentAborted) events.getLast()).reason()).isEqualTo("stop model");
    }

    @Test
    void emitsAgentAbortedWhenToolObservesAbortSignal() {
        ToolCall toolCall = new ToolCall("tool-1", "abort", JSON.objectNode());
        FakeModelClient model = new FakeModelClient()
                .enqueue(List.of(
                        new AiStreamEvent.MessageStarted("assistant-1"),
                        new AiStreamEvent.MessageCompleted(
                                "assistant-1",
                                new AiAssistantMessage(
                                        List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                                        AiStopReason.TOOL_USE,
                                        AiUsage.zero()))));
        ToolRegistry registry = InMemoryToolRegistry.builder()
                .register(new ToolSpec("abort", "Abort", JSON.objectNode()), (call, context) -> {
                    context.abortSignal().throwIfAborted();
                    return new ToolResult(call.id(), call.name(), false, JSON.textNode("unreachable"), JSON.objectNode());
                })
                .build();
        AbortController controller = new AbortController();
        AgentEventBus bus = new AgentEventBus();
        List<AgentEvent> events = new ArrayList<>();
        bus.subscribe(event -> {
            events.add(event);
            if (event instanceof AgentEvent.ToolExecutionStarted) {
                controller.abort("stop tool");
            }
        });

        assertThatThrownBy(() -> new AgentLoop(model, registry, bus)
                .runTurn(request(List.of(userMessage("user-1", "abort")), 2, controller.signal())))
                .isInstanceOf(AgentAbortException.class)
                .hasMessage("stop tool");
        assertThat(events.getLast()).isInstanceOf(AgentEvent.AgentAborted.class);
        assertThat(((AgentEvent.AgentAborted) events.getLast()).reason()).isEqualTo("stop tool");
    }

    @Test
    void defaultConverterFiltersSessionOnlyMessagesBeforeModelRequest() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueue(List.of(
                new AiStreamEvent.MessageStarted("assistant-1"),
                new AiStreamEvent.MessageCompleted(
                        "assistant-1",
                        new AiAssistantMessage(
                                List.of(new AiTextContent("done")),
                                AiStopReason.STOP,
                                AiUsage.zero()))));

        new AgentLoop(model, InMemoryToolRegistry.builder().build(), new AgentEventBus())
                .runTurn(request(List.of(
                        customMessage("custom-1", AgentMessageRole.BASH_EXECUTION, "ls -la"),
                        customMessage("custom-2", AgentMessageRole.CUSTOM, "ui only"),
                        userMessage("user-1", "continue")), 1));

        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().getFirst().messages()).extracting(AiMessage::role)
                .containsExactly("user");
    }

    @Test
    void acceptsCustomConvertToLlmBoundaryForSessionMessages() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueue(List.of(
                new AiStreamEvent.MessageStarted("assistant-1"),
                new AiStreamEvent.MessageCompleted(
                        "assistant-1",
                        new AiAssistantMessage(
                                List.of(new AiTextContent("done")),
                                AiStopReason.STOP,
                                AiUsage.zero()))));
        AgentMessageConverter converter = messages -> messages.stream()
                .map(message -> switch (message.role()) {
                    case BASH_EXECUTION -> Optional.<AiMessage>of(com.agent4j.ai.AiUserMessage.text("bash output: " + message.textContent()));
                    case USER -> Optional.<AiMessage>of(new com.agent4j.ai.AiUserMessage(com.agent4j.ai.AiContentBlocks.parse(message.content())));
                    default -> Optional.<AiMessage>empty();
                })
                .flatMap(Optional::stream)
                .toList();

        new AgentLoop(model, InMemoryToolRegistry.builder().build(), new AgentEventBus(), converter)
                .runTurn(request(List.of(
                        customMessage("bash-1", AgentMessageRole.BASH_EXECUTION, "file list"),
                        userMessage("user-1", "summarize")), 1));

        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().getFirst().messages()).extracting(AiMessage::role)
                .containsExactly("user", "user");
        assertThat(((com.agent4j.ai.AiUserMessage) model.requests().getFirst().messages().getFirst())
                .content().getFirst()).isEqualTo(new AiTextContent("bash output: file list"));
    }

    private AgentLoopRequest request(List<AgentMessage> messages, int maxToolRounds) {
        return request(messages, maxToolRounds, new AbortController().signal());
    }

    private AgentLoopRequest request(List<AgentMessage> messages, int maxToolRounds, int maxModelRetries) {
        return new AgentLoopRequest(
                "session-1",
                "turn-1",
                messages.getLast().id(),
                messages,
                Path.of("/repo"),
                clock,
                new AbortController().signal(),
                Map.of(),
                maxToolRounds,
                maxModelRetries,
                Optional.empty(),
                List.of(messages.getLast()),
                List.of(),
                List.of(),
                QueueMode.ONE_AT_A_TIME,
                QueueMode.ONE_AT_A_TIME);
    }

    private AgentLoopRequest request(List<AgentMessage> messages, int maxToolRounds, ToolExecutionMode toolExecutionMode) {
        return new AgentLoopRequest(
                "session-1",
                "turn-1",
                messages.getLast().id(),
                messages,
                Path.of("/repo"),
                clock,
                new AbortController().signal(),
                Map.of(),
                null,
                maxToolRounds,
                0,
                Optional.empty(),
                toolExecutionMode,
                List.of(messages.getLast()),
                List.of(),
                List.of(),
                QueueMode.ONE_AT_A_TIME,
                QueueMode.ONE_AT_A_TIME);
    }

    private AgentLoopRequest request(List<AgentMessage> messages, int maxToolRounds, AbortSignal signal) {
        return new AgentLoopRequest(
                "session-1",
                "turn-1",
                messages.getLast().id(),
                messages,
                Path.of("/repo"),
                clock,
                signal,
                Map.of(),
                maxToolRounds);
    }

    private AgentLoopRequest request(
            List<AgentMessage> messages,
            int maxToolRounds,
            List<AgentMessage> promptMessages,
            List<AgentMessage> steeringMessages,
            List<AgentMessage> followUpMessages
    ) {
        return request(
                messages,
                maxToolRounds,
                promptMessages,
                steeringMessages,
                followUpMessages,
                QueueMode.ONE_AT_A_TIME,
                QueueMode.ONE_AT_A_TIME);
    }

    private AgentLoopRequest request(
            List<AgentMessage> messages,
            int maxToolRounds,
            List<AgentMessage> promptMessages,
            List<AgentMessage> steeringMessages,
            List<AgentMessage> followUpMessages,
            QueueMode steeringMode,
            QueueMode followUpMode
    ) {
        return new AgentLoopRequest(
                "session-1",
                "turn-1",
                messages.getLast().id(),
                messages,
                Path.of("/repo"),
                clock,
                new AbortController().signal(),
                Map.of(),
                maxToolRounds,
                0,
                Optional.empty(),
                promptMessages,
                steeringMessages,
                followUpMessages,
                steeringMode,
                followUpMode);
    }

    private AgentMessage userMessage(String id, String text) {
        return new AgentMessage(
                id,
                null,
                Instant.now(clock),
                AgentMessageRole.USER,
                ContentBlocks.toJsonArray(List.of(new TextBlock(text, null))),
                JSON.objectNode());
    }

    private AgentMessage customMessage(String id, AgentMessageRole role, String text) {
        return new AgentMessage(
                id,
                null,
                Instant.now(clock),
                role,
                ContentBlocks.toJsonArray(List.of(new TextBlock(text, null))),
                JSON.objectNode());
    }
}
