package com.agent4j.ai.openai;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiContentBlock;
import com.agent4j.ai.AiGenerationOptions;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelFeatures;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderFeatures;
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
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiResponsesProviderTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void serializesTurnRequestToResponsesApiRequestBody() {
        AiModel model = new AiModel(new AiModelReference("openai", "gpt-5"), "GPT-5");
        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(
                OpenAiResponsesProviderOptions.defaults(List.of(model)),
                new CapturingTransport(List.of()));
        AiProviderRequest request = new AiProviderRequest(
                model,
                new AiTurnRequest(
                        List.of(
                                new AiSystemMessage("Follow project instructions."),
                                AiUserMessage.text("Read README.md"),
                                new AiToolResultMessage(
                                        "call-1",
                                        "read",
                                        List.of(new AiTextContent("README content")),
                                        false)),
                        List.of(new AiToolSpec("read", "Read a file", JSON.objectNode()
                                .put("type", "object")
                                .set("properties", JSON.objectNode())))),
                AiProviderContext.empty(),
                AiStreamOptions.defaults());

        JsonNode body = provider.toRequestJson(request);

        assertThat(body.get("model").asText()).isEqualTo("gpt-5");
        assertThat(body.get("stream").asBoolean()).isTrue();
        assertThat(body.get("instructions").asText()).isEqualTo("Follow project instructions.");
        assertThat(body.get("input")).hasSize(2);
        assertThat(body.at("/input/0/role").asText()).isEqualTo("user");
        assertThat(body.at("/input/0/content/0/type").asText()).isEqualTo("input_text");
        assertThat(body.at("/input/1/type").asText()).isEqualTo("function_call_output");
        assertThat(body.at("/input/1/call_id").asText()).isEqualTo("call-1");
        assertThat(body.at("/tools/0/type").asText()).isEqualTo("function");
        assertThat(body.at("/tools/0/name").asText()).isEqualTo("read");
        assertThat(body.at("/tool_choice").asText()).isEqualTo("auto");
    }

    @Test
    void serializesCommonGenerationOptionsToResponsesApiRequestBody() {
        AiModel model = new AiModel(new AiModelReference("openai", "gpt-5"), "GPT-5");
        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(
                OpenAiResponsesProviderOptions.defaults(List.of(model)),
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
                                Optional.empty(),
                                Optional.of("required"),
                                false,
                                Map.of("session", "abc"))));

        JsonNode body = provider.toRequestJson(request);

        assertThat(body.get("max_output_tokens").asInt()).isEqualTo(2048);
        assertThat(body.get("temperature").asDouble()).isEqualTo(0.3);
        assertThat(body.get("top_p").asDouble()).isEqualTo(0.8);
        assertThat(body.get("tool_choice").asText()).isEqualTo("required");
        assertThat(body.get("parallel_tool_calls").asBoolean()).isFalse();
        assertThat(body.at("/metadata/session").asText()).isEqualTo("abc");
    }

    @Test
    void omitsToolFieldsWhenModelDisablesToolCalling() {
        AiModel model = new AiModel(
                new AiModelReference("openai", "gpt-5-no-tools"),
                "GPT-5 No Tools",
                Optional.empty(),
                Optional.empty(),
                false,
                Map.of(),
                Set.of(),
                Set.of(),
                128000,
                16384,
                com.agent4j.ai.AiCost.zero(),
                com.agent4j.ai.AiModelCompat.defaults(),
                new AiModelFeatures(true, false, false, false, false, false, false, true, true, false));
        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(
                OpenAiResponsesProviderOptions.defaults(List.of(model)),
                new CapturingTransport(List.of()));
        AiProviderRequest request = new AiProviderRequest(
                model,
                new AiTurnRequest(
                        List.of(AiUserMessage.text("hello")),
                        List.of(new AiToolSpec("read", "Read a file", JSON.objectNode()))),
                AiProviderContext.empty(),
                AiStreamOptions.defaults());

        JsonNode body = provider.toRequestJson(request);

        assertThat(body.has("tools")).isFalse();
        assertThat(body.has("tool_choice")).isFalse();
        assertThat(body.has("parallel_tool_calls")).isFalse();
    }

    @Test
    void exposesProviderFeatureFlagsFromOptions() {
        AiModel model = new AiModel(new AiModelReference("openai", "gpt-5"), "GPT-5");
        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(
                new OpenAiResponsesProviderOptions(
                        "openai",
                        "OpenAI",
                        URI.create("https://api.openai.com/v1/responses"),
                        List.of(model),
                        Map.of(),
                        request -> request,
                        new AiProviderFeatures(true, true, true, true, true, true, true, true, false, false)),
                new CapturingTransport(List.of()));

        assertThat(provider.features().toolChoice()).isFalse();
        assertThat(provider.features().parallelToolCalls()).isFalse();
    }

    @Test
    void sendsHeadersAndNormalizesResponsesSseStream() throws Exception {
        AiModel model = new AiModel(new AiModelReference("openai", "gpt-5"), "GPT-5");
        CapturingTransport transport = new CapturingTransport(List.of(
                "data: {\"type\":\"response.created\",\"response\":{\"id\":\"res_1\"}}",
                "",
                "data: {\"type\":\"response.content_part.added\",\"item_id\":\"msg_1\",\"content_index\":0,\"part\":{\"type\":\"output_text\"}}",
                "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"msg_1\",\"content_index\":0,\"delta\":\"Reading\"}",
                "data: {\"type\":\"response.output_text.done\",\"item_id\":\"msg_1\",\"content_index\":0,\"text\":\"Reading\"}",
                "data: {\"type\":\"response.output_item.added\",\"output_index\":1,\"item\":{\"id\":\"fc_1\",\"type\":\"function_call\",\"call_id\":\"call_1\",\"name\":\"read\"}}",
                "data: {\"type\":\"response.function_call_arguments.delta\",\"item_id\":\"fc_1\",\"output_index\":1,\"delta\":\"{\\\"path\\\":\"}",
                "data: {\"type\":\"response.function_call_arguments.done\",\"item_id\":\"fc_1\",\"output_index\":1,\"name\":\"read\",\"arguments\":\"{\\\"path\\\":\\\"README.md\\\"}\"}",
                "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"res_1\",\"status\":\"completed\",\"usage\":{\"input_tokens\":10,\"output_tokens\":5,\"input_tokens_details\":{\"cached_tokens\":2},\"output_tokens_details\":{\"reasoning_tokens\":1}}}}",
                "data: [DONE]"));
        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(
                new OpenAiResponsesProviderOptions(
                        "openai",
                        "OpenAI",
                        URI.create("https://api.openai.com/v1/responses"),
                        List.of(model),
                        Map.of("OpenAI-Beta", "responses=v1"),
                        request -> request),
                transport);
        AiProviderRequest request = new AiProviderRequest(
                model,
                new AiTurnRequest(List.of(AiUserMessage.text("hello")), List.of()),
                new AiProviderContext(
                        Optional.of("session-1"),
                        Optional.of("turn-1"),
                        Optional.empty(),
                        new AiResolvedAuth(
                                Optional.of("sk-test"),
                                Map.of("X-Test", "yes"),
                                Optional.empty(),
                                Optional.of("test"),
                                Map.of()),
                        Map.of(),
                        Map.of()),
                new AiStreamOptions(null, Optional.of(Duration.ofSeconds(10)), 1, Map.of("X-Request", "1"), Map.of()));
        List<AiStreamEvent> events = new ArrayList<>();

        provider.stream(request, events::add);

        assertThat(transport.request.headers()).containsEntry("Authorization", "Bearer sk-test");
        assertThat(transport.request.headers()).containsEntry("Accept", "text/event-stream");
        assertThat(transport.request.headers()).containsEntry("OpenAI-Beta", "responses=v1");
        assertThat(transport.request.headers()).containsEntry("X-Test", "yes");
        assertThat(transport.request.headers()).containsEntry("X-Request", "1");
        assertThat(transport.request.uri()).isEqualTo(URI.create("https://api.openai.com/v1/responses"));
        assertThat(transport.request.timeout()).contains(Duration.ofSeconds(10));
        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsExactly(
                        "MessageStarted",
                        "TextStarted",
                        "TextDelta",
                        "TextEnded",
                        "ToolCallStarted",
                        "ToolCallDelta",
                        "ToolCallEnded",
                        "MessageCompleted");
        assertThat(events.get(4)).isInstanceOfSatisfying(AiStreamEvent.ToolCallStarted.class, event -> {
            assertThat(event.toolCallId()).isEqualTo("call_1");
            assertThat(event.toolName()).isEqualTo("read");
        });
        assertThat(events.getLast()).isInstanceOfSatisfying(AiStreamEvent.MessageCompleted.class, event -> {
            assertThat(event.message().stopReason()).isEqualTo(AiStopReason.TOOL_USE);
            assertThat(event.message().usage()).isEqualTo(new AiUsage(10, 5, 2, 1));
            assertThat(event.message().content()).containsExactly(
                    new AiTextContent("Reading"),
                    new AiToolCallContent("call_1", "read", JSON.objectNode().put("path", "README.md")));
        });
    }

    @Test
    void resolvesEndpointFromEffectiveModelBaseUrl() throws Exception {
        AiModel model = new AiModel(new AiModelReference("openai", "gpt-5"), "GPT-5")
                .withBaseUrl("https://catalog.openai.test/v1");
        CapturingTransport transport = new CapturingTransport(List.of(
                "data: {\"type\":\"response.created\",\"response\":{\"id\":\"res_1\"}}",
                "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"res_1\",\"status\":\"completed\",\"usage\":{}}}",
                "data: [DONE]"));
        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(
                OpenAiResponsesProviderOptions.defaults(List.of(model)),
                transport);
        AiProviderRequest request = new AiProviderRequest(
                model,
                new AiTurnRequest(List.of(AiUserMessage.text("hello")), List.of()),
                new AiProviderContext(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        new AiResolvedAuth(
                                Optional.empty(),
                                Map.of(),
                                Optional.of("https://auth.openai.test/v1"),
                                Optional.of("test"),
                                Map.of()),
                        Map.of(),
                        Map.of()),
                AiStreamOptions.defaults());

        provider.stream(request, event -> {
        });

        assertThat(request.model().baseUrl()).contains("https://auth.openai.test/v1");
        assertThat(transport.request.uri()).isEqualTo(URI.create("https://auth.openai.test/v1/responses"));
    }

    @Test
    void accumulatesTextDeltasIntoFinalAssistantMessageLikePiResponsesStream() throws Exception {
        AiModel model = new AiModel(new AiModelReference("openai", "gpt-5"), "GPT-5");
        CapturingTransport transport = new CapturingTransport(List.of(
                "data: {\"type\":\"response.created\",\"response\":{\"id\":\"res_1\"}}",
                "data: {\"type\":\"response.output_item.added\",\"output_index\":0,\"item\":{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[]}}",
                "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"msg_1\",\"output_index\":0,\"content_index\":0,\"delta\":\"Hel\"}",
                "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"msg_1\",\"output_index\":0,\"content_index\":0,\"delta\":\"lo\"}",
                "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"res_1\",\"status\":\"completed\",\"usage\":{}}}",
                "data: [DONE]"));
        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(
                OpenAiResponsesProviderOptions.defaults(List.of(model)),
                transport);
        List<AiStreamEvent> events = new ArrayList<>();

        provider.stream(new AiProviderRequest(
                model,
                new AiTurnRequest(List.of(AiUserMessage.text("hello")), List.of()),
                AiProviderContext.empty(),
                AiStreamOptions.defaults()), events::add);

        assertThat(events).extracting(event -> event.getClass().getSimpleName())
                .containsExactly("MessageStarted", "TextDelta", "TextDelta", "MessageCompleted");
        assertThat(events.getLast()).isInstanceOfSatisfying(AiStreamEvent.MessageCompleted.class, event ->
                assertThat(event.message().content()).containsExactly(new AiTextContent("Hello")));
    }

    private static final class CapturingTransport implements OpenAiTransport {
        private final List<String> lines;
        private OpenAiHttpRequest request;

        private CapturingTransport(List<String> lines) {
            this.lines = List.copyOf(lines);
        }

        @Override
        public void stream(OpenAiHttpRequest request, Consumer<String> lineSink) {
            this.request = request;
            lines.forEach(lineSink);
        }
    }
}
