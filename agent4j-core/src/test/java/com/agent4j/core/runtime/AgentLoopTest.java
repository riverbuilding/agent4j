package com.agent4j.core.runtime;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiThinkingContent;
import com.agent4j.ai.AiToolCallContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.ToolRegistry;
import com.agent4j.core.tool.ToolSpec;
import com.agent4j.testkit.ai.FakeModelClient;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
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
                maxToolRounds,
                0,
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
                promptMessages,
                steeringMessages,
                followUpMessages,
                QueueMode.ONE_AT_A_TIME,
                QueueMode.ONE_AT_A_TIME);
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
