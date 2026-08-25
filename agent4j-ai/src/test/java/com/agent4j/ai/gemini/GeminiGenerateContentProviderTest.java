package com.agent4j.ai.gemini;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderContext;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.ai.AiSystemMessage;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiToolSpec;
import com.agent4j.ai.AiTurnRequest;
import com.agent4j.ai.AiUserMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiGenerateContentProviderTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void serializesGeminiRequestAndNormalizesAnSseResponse() throws Exception {
        AiModel model = new AiModel(new AiModelReference("gemini", "gemini-2.5-pro"), "Gemini 2.5 Pro");
        CapturingTransport transport = new CapturingTransport(List.of(
                "data: {\"candidates\":[{\"index\":0,\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Hello\"}]}}]}",
                "",
                "data: {\"candidates\":[{\"index\":0,\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"read\",\"args\":{\"path\":\"README.md\"}}}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":3,\"candidatesTokenCount\":5}}",
                ""));
        GeminiGenerateContentProvider provider = new GeminiGenerateContentProvider(List.of(model), transport);
        AiProviderRequest request = new AiProviderRequest(model, new AiTurnRequest(
                List.of(new AiSystemMessage("Follow instructions."), AiUserMessage.text("Read README.md")),
                List.of(new AiToolSpec("read", "Read a file", JSON.objectNode().put("type", "object")))),
                AiProviderContext.empty(), AiStreamOptions.defaults());

        JsonNode body = provider.toRequestJson(request);
        List<AiStreamEvent> events = new ArrayList<>();
        provider.stream(request, events::add);

        assertThat(body.at("/systemInstruction/parts/0/text").asText()).isEqualTo("Follow instructions.");
        assertThat(body.at("/contents/0/role").asText()).isEqualTo("user");
        assertThat(body.at("/tools/0/functionDeclarations/0/name").asText()).isEqualTo("read");
        assertThat(transport.request.uri()).isEqualTo(URI.create(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:streamGenerateContent?alt=sse"));
        assertThat(events).extracting(event -> event.getClass().getSimpleName()).contains(
                "MessageStarted", "TextDelta", "ToolCallStarted", "ToolCallEnded", "MessageCompleted");
        assertThat(events.getLast()).isInstanceOfSatisfying(AiStreamEvent.MessageCompleted.class, event -> {
            assertThat(event.message().content()).contains(new AiTextContent("Hello"));
            assertThat(event.message().usage().inputTokens()).isEqualTo(3);
            assertThat(event.message().usage().outputTokens()).isEqualTo(5);
        });
    }

    private static final class CapturingTransport implements GeminiTransport {
        private final List<String> lines;
        private GeminiHttpRequest request;

        private CapturingTransport(List<String> lines) { this.lines = lines; }
        @Override public void stream(GeminiHttpRequest request, Consumer<String> lineSink) {
            this.request = request;
            lines.forEach(lineSink);
        }
    }
}
