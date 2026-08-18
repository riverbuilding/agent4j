package com.agent4j.coding.extension;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderRegistry;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiSystemMessage;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiUsage;
import com.agent4j.ai.AiUserMessage;
import com.agent4j.coding.sdk.AgentSession;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CreateSessionRequest;
import com.agent4j.coding.sdk.PromptRequest;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.testkit.ai.FakeModelClient;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionPromptHookIntegrationTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @TempDir
    Path tempDir;

    @Test
    void transformsModelInputInOrderWithoutChangingPersistedSessionHistory() throws Exception {
        FakeModelClient model = new FakeModelClient().enqueue(List.of(new AiStreamEvent.MessageCompleted(
                "assistant-1",
                new AiAssistantMessage(List.of(new AiTextContent("done")), AiStopReason.STOP, AiUsage.zero()))));
        List<String> invocationOrder = new ArrayList<>();
        AgentExtension extension = new AgentExtension() {
            @Override
            public String name() {
                return "prompt-hooks";
            }

            @Override
            public void register(ExtensionContext context, ExtensionContributionRegistrar registrar) {
                registrar.registerAgentStartHook("first", (event, hookContext) -> {
                    invocationOrder.add("start:first");
                    return new CodingExtensionAgentStart(event.prompt() + "-first", "ephemeral-first");
                });
                registrar.registerAgentStartHook("second", (event, hookContext) -> {
                    invocationOrder.add("start:second");
                    return new CodingExtensionAgentStart(event.prompt() + "-second", event.systemPrompt() + "-second");
                });
                registrar.registerContextTransformHook("failing", (messages, hookContext) -> {
                    invocationOrder.add("context:failing");
                    throw new IllegalStateException("expected test failure");
                });
                registrar.registerContextTransformHook("rewrite", (messages, hookContext) -> {
                    invocationOrder.add("context:rewrite");
                    AgentMessage last = messages.getLast();
                    AgentMessage rewritten = new AgentMessage(
                            last.id(), last.parentId(), last.timestamp(), last.role(),
                            JSON.textNode(last.textContent() + "-context"), last.metadata());
                    return java.util.stream.Stream.concat(messages.subList(0, messages.size() - 1).stream(),
                            java.util.stream.Stream.of(rewritten)).toList();
                });
            }
        };
        CodingAgentRuntime runtime = CodingAgentRuntime.builder()
                .providerRegistry(AiProviderRegistry.fixedClient(
                        new AiModel(new AiModelReference("test", "fixed"), "Fixed model"), model))
                .extensionLoader(ExtensionLoader.builder().addExtension(extension).build())
                .build();
        AgentSession session = runtime.createSession(new CreateSessionRequest(tempDir.resolve("session.jsonl"), tempDir));

        session.prompt(new PromptRequest("original prompt"));

        assertThat(invocationOrder).containsExactly(
                "start:first", "start:second", "context:failing", "context:rewrite");
        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().getFirst().messages()).containsExactly(
                new AiSystemMessage("ephemeral-first-second"),
                new AiUserMessage(List.of(new AiTextContent("original prompt-first-second-context"))));
        assertThat(session.conversationContext().transcriptMessages())
                .extracting(AgentMessage::textContent)
                .containsExactly("original prompt", "done");
    }
}
