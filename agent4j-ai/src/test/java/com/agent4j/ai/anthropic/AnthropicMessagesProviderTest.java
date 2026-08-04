package com.agent4j.ai.anthropic;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiGenerationOptions;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderContext;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiResolvedAuth;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.ai.AiSystemMessage;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiToolCallContent;
import com.agent4j.ai.AiToolResultMessage;
import com.agent4j.ai.AiToolSpec;
import com.agent4j.ai.AiTurnRequest;
import com.agent4j.ai.AiUsage;
import com.agent4j.ai.AiUserMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicMessagesProviderTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void serializesTurnRequestToMessagesApiRequestBody() {
        AiModel model = new AiModel(new AiModelReference("anthropic", "claude-sonnet-4-5"), "Claude Sonnet 4.5");
        AnthropicMessagesProvider provider = new AnthropicMessagesProvider(
                AnthropicMessagesProviderOptions.defaults(List.of(model)),
                new CapturingTransport(List.of()));
        AiProviderRequest request = new AiProviderRequest(
                model,
                new AiTurnRequest(
                        List.of(
                                new AiSystemMessage("Follow project instructions."),
                                AiUserMessage.text("Read README.md"),
                                new AiAssistantMessage(
                                        List.of(new AiToolCallContent("toolu_1", "read", JSON.objectNode().put("path", "README.md"))),
                                        AiStopReason.TOOL_USE,
                                        AiUsage.zero()),
                                new AiToolResultMessage(
                                        "toolu_1",
                                        "read",
                                        List.of(new AiTextContent("README content")),
                                        false)),
                        List.of(new AiToolSpec("read", "Read a file", JSON.objectNode()
                                .put("type", "object")
                                .set("properties", JSON.objectNode())))),
                AiProviderContext.empty(),
                AiStreamOptions.defaults());

        JsonNode body = provider.toRequestJson(request);

        assertThat(body.get("model").asText()).isEqualTo("claude-sonnet-4-5");
        assertThat(body.get("stream").asBoolean()).isTrue();
        assertThat(body.get("max_tokens").asLong()).isEqualTo(model.maxTokens());
        assertThat(body.get("system").asText()).isEqualTo("Follow project instructions.");
        assertThat(body.get("messages")).hasSize(3);
        assertThat(body.at("/messages/0/role").asText()).isEqualTo("user");
        assertThat(body.at("/messages/0/content/0/type").asText()).isEqualTo("text");
        assertThat(body.at("/messages/1/role").asText()).isEqualTo("assistant");
        assertThat(body.at("/messages/1/content/0/type").asText()).isEqualTo("tool_use");
        assertThat(body.at("/messages/1/content/0/id").asText()).isEqualTo("toolu_1");
        assertThat(body.at("/messages/2/role").asText()).isEqualTo("user");
        assertThat(body.at("/messages/2/content/0/type").asText()).isEqualTo("tool_result");
        assertThat(body.at("/messages/2/content/0/tool_use_id").asText()).isEqualTo("toolu_1");
        assertThat(body.at("/tools/0/name").asText()).isEqualTo("read");
        assertThat(body.at("/tools/0/input_schema/type").asText()).isEqualTo("object");
    }

    @Test
    void serializesCommonGenerationOptionsToMessagesApiRequestBody() {
        AiModel model = new AiModel(new AiModelReference("anthropic", "claude-sonnet-4-5"), "Claude Sonnet 4.5");
        AnthropicMessagesProvider provider = new AnthropicMessagesProvider(
                AnthropicMessagesProviderOptions.defaults(List.of(model)),
                new CapturingTransport(List.of()));
        AiProviderRequest request = new AiProviderRequest(
                model,
                new AiTurnRequest(
                        List.of(AiUserMessage.text("hello")),
                        List.of(new AiToolSpec("read", "Read a file", JSON.objectNode()))),
                AiProviderContext.empty(),
                new AiStreamOptions(
                        null,
                        Optional.empty(),
                        0,
                        Map.of(),
                        Map.of(),
                        new AiGenerationOptions(
                                Optional.of(2048),
                                Optional.of(0.3),
                                Optional.of(0.8),
                                Optional.of(40),
                                Optional.of("tool:read"),
                                true,
                                Map.of("session", "abc"))));

        JsonNode body = provider.toRequestJson(request);

        assertThat(body.get("max_tokens").asInt()).isEqualTo(2048);
        assertThat(body.get("temperature").asDouble()).isEqualTo(0.3);
        assertThat(body.get("top_p").asDouble()).isEqualTo(0.8);
        assertThat(body.get("top_k").asInt()).isEqualTo(40);
        assertThat(body.at("/tool_choice/type").asText()).isEqualTo("tool");
        assertThat(body.at("/tool_choice/name").asText()).isEqualTo("read");
        assertThat(body.at("/metadata/session").asText()).isEqualTo("abc");
    }

    @Test
    void sendsHeadersAndNormalizesMessagesSseStream() throws Exception {
        AiModel model = new AiModel(new AiModelReference("anthropic", "claude-sonnet-4-5"), "Claude Sonnet 4.5");
        CapturingTransport transport = new CapturingTransport(List.of(
                "event: message_start",
                "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[],\"model\":\"claude\",\"stop_reason\":null,\"usage\":{\"input_tokens\":10,\"cache_read_input_tokens\":2}}}",
                "",
                "event: content_block_start",
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
                "",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}",
                "",
                "event: content_block_stop",
                "data: {\"type\":\"content_block_stop\",\"index\":0}",
                "",
                "event: content_block_start",
                "data: {\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"read\",\"input\":{}}}",
                "",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"path\\\":\"}}",
                "",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"README.md\\\"}\"}}",
                "",
                "event: content_block_stop",
                "data: {\"type\":\"content_block_stop\",\"index\":1}",
                "",
                "event: message_delta",
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":5}}",
                "",
                "event: message_stop",
                "data: {\"type\":\"message_stop\"}",
                ""));
        AnthropicMessagesProvider provider = new AnthropicMessagesProvider(
                new AnthropicMessagesProviderOptions(
                        "anthropic",
                        "Anthropic",
                        URI.create("https://api.anthropic.com/v1/messages"),
                        List.of(model),
                        Map.of("anthropic-beta", "fine-grained-tool-streaming-2025-05-14"),
                        request -> request,
                        "2023-06-01"),
                transport);
        AiProviderRequest request = new AiProviderRequest(
                model,
                new AiTurnRequest(List.of(AiUserMessage.text("hello")), List.of()),
                new AiProviderContext(
                        Optional.of("session-1"),
                        Optional.of("turn-1"),
                        Optional.empty(),
                        new AiResolvedAuth(
                                Optional.of("sk-ant-test"),
                                Map.of("X-Test", "yes"),
                                Optional.empty(),
                                Optional.of("test"),
                                Map.of()),
                        Map.of(),
                        Map.of()),
                new AiStreamOptions(null, Optional.of(Duration.ofSeconds(10)), 1, Map.of("X-Request", "1"), Map.of()));
        List<AiStreamEvent> events = new ArrayList<>();

        provider.stream(request, events::add);

        assertThat(transport.request.headers()).containsEntry("x-api-key", "sk-ant-test");
        assertThat(transport.request.headers()).containsEntry("Accept", "text/event-stream");
        assertThat(transport.request.headers()).containsEntry("anthropic-version", "2023-06-01");
        assertThat(transport.request.headers()).containsEntry("anthropic-beta", "fine-grained-tool-streaming-2025-05-14");
        assertThat(transport.request.headers()).containsEntry("X-Test", "yes");
        assertThat(transport.request.headers()).containsEntry("X-Request", "1");
        assertThat(transport.request.timeout()).contains(Duration.ofSeconds(10));
        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsExactly(
                        "MessageStarted",
                        "TextStarted",
                        "TextDelta",
                        "TextEnded",
                        "ToolCallStarted",
                        "ToolCallDelta",
                        "ToolCallDelta",
                        "ToolCallEnded",
                        "MessageCompleted");
        assertThat(events.get(4)).isInstanceOfSatisfying(AiStreamEvent.ToolCallStarted.class, event -> {
            assertThat(event.toolCallId()).isEqualTo("toolu_1");
            assertThat(event.toolName()).isEqualTo("read");
        });
        assertThat(events.getLast()).isInstanceOfSatisfying(AiStreamEvent.MessageCompleted.class, event -> {
            assertThat(event.message().stopReason()).isEqualTo(AiStopReason.TOOL_USE);
            assertThat(event.message().usage()).isEqualTo(new AiUsage(10, 5, 2, 0));
            assertThat(event.message().content()).containsExactly(
                    new AiTextContent("Hello"),
                    new AiToolCallContent("toolu_1", "read", JSON.objectNode().put("path", "README.md")));
        });
    }

    private static final class CapturingTransport implements AnthropicTransport {
        private final List<String> lines;
        private AnthropicHttpRequest request;

        private CapturingTransport(List<String> lines) {
            this.lines = List.copyOf(lines);
        }

        @Override
        public void stream(AnthropicHttpRequest request, Consumer<String> lineSink) {
            this.request = request;
            lines.forEach(lineSink);
        }
    }
}
