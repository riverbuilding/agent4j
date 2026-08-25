package com.agent4j.coding.resource;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderContext;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.ai.AiSystemMessage;
import com.agent4j.ai.AiTurnRequest;
import com.agent4j.ai.AiUserMessage;
import com.agent4j.ai.anthropic.AnthropicMessagesProvider;
import com.agent4j.ai.openai.OpenAiResponsesProvider;
import com.agent4j.core.tool.ToolSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptProviderRequestTest {
    @TempDir
    Path tempDir;

    @Test
    void serializesComposedPromptAsOpenAiInstructions() throws Exception {
        String prompt = composedPrompt();
        AiModel model = new AiModel(new AiModelReference("openai", "gpt-5"), "GPT-5");
        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(List.of(model));

        JsonNode body = provider.toRequestJson(request(model, prompt));

        assertThat(body.path("instructions").asText())
                .contains(DefaultCodingSystemPrompt.VERSION, "project instruction", "read: Read a workspace file.");
    }

    @Test
    void serializesComposedPromptAsAnthropicSystem() throws Exception {
        String prompt = composedPrompt();
        AiModel model = new AiModel(new AiModelReference("anthropic", "claude-sonnet-4-5"), "Claude Sonnet");
        AnthropicMessagesProvider provider = new AnthropicMessagesProvider(List.of(model));

        JsonNode body = provider.toRequestJson(request(model, prompt));

        assertThat(body.path("system").asText())
                .contains(DefaultCodingSystemPrompt.VERSION, "project instruction", "read: Read a workspace file.");
    }

    private String composedPrompt() throws Exception {
        Path home = tempDir.resolve("home");
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("AGENTS.md"), "project instruction");
        ResourceDiscovery discovery = new ResourceLoader().discover(ResourceDiscoveryOptions.enabled(home, workspace));
        return new SystemPromptBuilder().build(
                discovery,
                List.of(new ToolSpec("read", "Read a workspace file.", JsonNodeFactory.instance.objectNode())),
                java.util.Optional.empty(),
                List.of());
    }

    private static AiProviderRequest request(AiModel model, String prompt) {
        return new AiProviderRequest(
                model,
                new AiTurnRequest(List.of(new AiSystemMessage(prompt), AiUserMessage.text("inspect the project")), List.of()),
                AiProviderContext.empty(),
                AiStreamOptions.defaults());
    }
}
