package com.agent4j.coding.extension;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderRegistry;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiToolCallContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CreateSessionRequest;
import com.agent4j.coding.sdk.PromptRequest;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.runtime.QueueMode;
import com.agent4j.core.runtime.ToolExecutionMode;
import com.agent4j.core.tool.ToolExecutionHook;
import com.agent4j.core.tool.ToolSpec;
import com.agent4j.testkit.ai.FakeModelClient;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionToolRuntimeIntegrationTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @TempDir
    Path tempDir;

    @Test
    void resolvesProgrammaticExtensionsOnceWhenTheRuntimeIsCreated() throws Exception {
        AtomicInteger registrations = new AtomicInteger();
        AgentExtension extension = new AgentExtension() {
            @Override
            public String name() {
                return "runtime-extension";
            }

            @Override
            public void register(ExtensionContext context, ExtensionContributionRegistrar registrar) {
                registrations.incrementAndGet();
            }
        };

        CodingAgentRuntime runtime = CodingAgentRuntime.builder()
                .extensionLoader(ExtensionLoader.builder().addExtension(extension).build())
                .build();
        runtime.createSession(new CreateSessionRequest(tempDir.resolve("first.jsonl"), tempDir));
        runtime.createSession(new CreateSessionRequest(tempDir.resolve("second.jsonl"), tempDir));

        assertThat(registrations).hasValue(1);
    }

    @Test
    void doesNotRegisterProjectScopedExtensionsForAnUntrustedRuntime() {
        AtomicInteger registrations = new AtomicInteger();
        AgentExtension extension = new AgentExtension() {
            @Override
            public String name() {
                return "project-extension";
            }

            @Override
            public ExtensionScope scope() {
                return ExtensionScope.PROJECT;
            }

            @Override
            public void register(ExtensionContext context, ExtensionContributionRegistrar registrar) {
                registrations.incrementAndGet();
            }
        };

        CodingAgentRuntime.builder()
                .extensionLoader(ExtensionLoader.builder().addExtension(extension).build())
                .extensionContext(new ExtensionContext(tempDir, null, false))
                .build();

        assertThat(registrations).hasValue(0);
    }

    @Test
    void extensionToolReceivesContextChangedByItsHookWithinCoreToolTiming() throws Exception {
        ToolCall toolCall = new ToolCall("tool-1", "extension_tool", JSON.objectNode());
        FakeModelClient model = new FakeModelClient()
                .enqueue(toolUse("assistant-1", toolCall))
                .enqueue(text("assistant-2", "done"));
        AgentEventBus eventBus = new AgentEventBus();
        List<String> executionOrder = new ArrayList<>();
        List<AgentEvent> events = new ArrayList<>();
        eventBus.subscribe(event -> {
            events.add(event);
            if (event instanceof AgentEvent.ToolExecutionStarted) {
                executionOrder.add("start");
            } else if (event instanceof AgentEvent.ToolExecutionEnded) {
                executionOrder.add("end");
            }
        });
        AgentExtension extension = new AgentExtension() {
            @Override
            public String name() {
                return "context-extension";
            }

            @Override
            public void register(ExtensionContext context, ExtensionContributionRegistrar registrar) {
                registrar.registerHook("set-visible-context", new ToolExecutionHook() {
                    @Override
                    public java.util.Optional<ToolResult> beforeToolExecution(ToolCall call,
                                                                             com.agent4j.core.tool.ToolContext toolContext) {
                        executionOrder.add("before");
                        toolContext.putAttribute("extension-value", "visible");
                        return java.util.Optional.empty();
                    }

                    @Override
                    public void afterToolExecution(ToolCall call, com.agent4j.core.tool.ToolContext toolContext,
                                                   ToolResult result) {
                        executionOrder.add("after");
                    }
                });
                registrar.registerTool(new ToolSpec("extension_tool", "Extension tool", JSON.objectNode()),
                        (call, toolContext) -> {
                            executionOrder.add("tool:" + toolContext.attribute("extension-value").orElseThrow());
                            return new ToolResult(call.id(), call.name(), false, TextNode.valueOf("ok"), JSON.objectNode());
                        });
            }
        };
        CodingAgentRuntime runtime = CodingAgentRuntime.builder()
                .eventBus(eventBus)
                .providerRegistry(AiProviderRegistry.fixedClient(
                        new AiModel(new AiModelReference("test", "fixed"), "Fixed model"), model))
                .extensionLoader(ExtensionLoader.builder().addExtension(extension).build())
                .build();
        AgentSession session = runtime.createSession(new CreateSessionRequest(tempDir.resolve("session.jsonl"), tempDir));

        session.prompt(new PromptRequest(
                "run extension tool",
                Optional.empty(),
                1,
                0,
                Optional.empty(),
                ToolExecutionMode.SEQUENTIAL,
                Map.of(),
                List.of(),
                List.of(),
                QueueMode.ONE_AT_A_TIME,
                QueueMode.ONE_AT_A_TIME,
                Optional.empty()));

        assertThat(executionOrder).containsExactly("start", "before", "tool:visible", "after", "end");
        assertThat(events).filteredOn(AgentEvent.ToolExecutionStarted.class::isInstance).hasSize(1);
        assertThat(events).filteredOn(AgentEvent.ToolExecutionEnded.class::isInstance).hasSize(1);
        assertThat(session.conversationContext().transcriptMessages())
                .extracting(message -> message.textContent())
                .contains("done");
    }

    private static List<AiStreamEvent> toolUse(String messageId, ToolCall toolCall) {
        return List.of(new AiStreamEvent.MessageCompleted(
                messageId,
                new AiAssistantMessage(
                        List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                        AiStopReason.TOOL_USE,
                        AiUsage.zero())));
    }

    private static List<AiStreamEvent> text(String messageId, String value) {
        return List.of(new AiStreamEvent.MessageCompleted(
                messageId,
                new AiAssistantMessage(List.of(new AiTextContent(value)), AiStopReason.STOP, AiUsage.zero())));
    }
}
