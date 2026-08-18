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
import com.agent4j.ai.AiUserMessage;
import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CreateSessionRequest;
import com.agent4j.coding.sdk.PromptRequest;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.runtime.QueueMode;
import com.agent4j.core.runtime.ToolExecutionMode;
import com.agent4j.core.tool.ToolSpec;
import com.agent4j.testkit.ai.FakeModelClient;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceLoadedExtensionIntegrationTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @TempDir
    Path tempDir;

    @Test
    void serviceLoadedExtensionContributesAcrossTheJavaSpi() throws Exception {
        ServiceLoadedExtension.reset();
        ToolCall toolCall = new ToolCall("tool-1", "service_safe_tool", JSON.objectNode());
        FakeModelClient model = new FakeModelClient()
                .enqueue(toolUse("assistant-1", toolCall))
                .enqueue(text("assistant-2", "done"));

        try (URLClassLoader classLoader = serviceClassLoader()) {
            CodingAgentRuntime runtime = CodingAgentRuntime.builder()
                    .providerRegistry(AiProviderRegistry.fixedClient(
                            new AiModel(new AiModelReference("test", "fixed"), "Fixed model"), model))
                    .extensionLoader(ExtensionLoader.builder().applicationClassLoader(classLoader).build())
                    .build();
            AgentSession session = runtime.createSession(new CreateSessionRequest(tempDir.resolve("session.jsonl"), tempDir));

            session.prompt(new PromptRequest(
                    "original prompt",
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
            runtime.interactiveCommandRegistry().find("service-status").orElseThrow().command().handler()
                    .execute("check", new CodingExtensionContext(tempDir, session.sessionFile(), true));
        }

        assertThat(model.requests().getFirst().messages()).contains(
                new AiUserMessage(List.of(new AiTextContent("original prompt-service"))));
        assertThat(ServiceLoadedExtension.events).containsExactly(
                "before:CREATE", "after:CREATE", "tool", "command:check:true");
    }

    private URLClassLoader serviceClassLoader() throws Exception {
        Path serviceFile = tempDir.resolve("META-INF/services/" + AgentExtension.class.getName());
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, ServiceLoadedExtension.class.getName());
        return new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, getClass().getClassLoader());
    }

    private static List<AiStreamEvent> toolUse(String messageId, ToolCall toolCall) {
        return List.of(new AiStreamEvent.MessageCompleted(
                messageId,
                new AiAssistantMessage(
                        List.of(new AiToolCallContent(toolCall.id(), toolCall.name(), toolCall.arguments())),
                        AiStopReason.TOOL_USE,
                        AiUsage.zero())));
    }

    private static List<AiStreamEvent> text(String messageId, String text) {
        return List.of(new AiStreamEvent.MessageCompleted(
                messageId,
                new AiAssistantMessage(List.of(new AiTextContent(text)), AiStopReason.STOP, AiUsage.zero())));
    }

    public static final class ServiceLoadedExtension implements AgentExtension {
        private static final List<String> events = new ArrayList<>();

        static void reset() {
            events.clear();
        }

        @Override
        public String name() {
            return "service-loaded-integration";
        }

        @Override
        public void register(ExtensionContext context, ExtensionContributionRegistrar registrar) {
            registrar.registerTool(new ToolSpec("service_safe_tool", "Returns a fixed safe response", JSON.objectNode()),
                    (call, toolContext) -> {
                        events.add("tool");
                        return new ToolResult(call.id(), call.name(), false, TextNode.valueOf("safe"), JSON.objectNode());
                    });
            registrar.registerContextTransformHook("service-context", (messages, hookContext) -> {
                AgentMessage last = messages.getLast();
                AgentMessage transformed = new AgentMessage(
                        last.id(), last.parentId(), last.timestamp(), last.role(),
                        JSON.textNode(last.textContent() + "-service"), last.metadata());
                return java.util.stream.Stream.concat(messages.subList(0, messages.size() - 1).stream(),
                        java.util.stream.Stream.of(transformed)).toList();
            });
            registrar.registerLifecycleListener("service-lifecycle", new ExtensionLifecycleListener() {
                @Override
                public void beforeSessionOperation(ExtensionSessionOperation operation, ExtensionSessionContext context) {
                    events.add("before:" + operation);
                }

                @Override
                public void afterSessionOperation(ExtensionSessionOperation operation, ExtensionSessionContext context) {
                    events.add("after:" + operation);
                }
            });
            registrar.registerCommand(new CodingExtensionCommand("service-status", "Reports service extension status",
                    (arguments, commandContext) -> events.add("command:" + arguments + ":" + commandContext.projectTrusted())));
        }
    }
}
