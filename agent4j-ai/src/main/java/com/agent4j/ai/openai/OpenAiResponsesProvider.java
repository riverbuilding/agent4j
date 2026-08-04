package com.agent4j.ai.openai;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiContentBlock;
import com.agent4j.ai.AiGenerationOptions;
import com.agent4j.ai.AiImageContent;
import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderApi;
import com.agent4j.ai.AiProviderFeatures;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiSystemMessage;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiThinkingContent;
import com.agent4j.ai.AiToolCallContent;
import com.agent4j.ai.AiToolResultMessage;
import com.agent4j.ai.AiToolSpec;
import com.agent4j.ai.AiUsage;
import com.agent4j.ai.AiUserMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class OpenAiResponsesProvider implements AiProvider {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final OpenAiResponsesProviderOptions options;
    private final OpenAiTransport transport;
    private final ObjectMapper mapper;

    public OpenAiResponsesProvider(List<AiModel> models) {
        this(OpenAiResponsesProviderOptions.defaults(models), new DefaultOpenAiTransport(), new ObjectMapper());
    }

    public OpenAiResponsesProvider(OpenAiResponsesProviderOptions options, OpenAiTransport transport) {
        this(options, transport, new ObjectMapper());
    }

    public OpenAiResponsesProvider(
            OpenAiResponsesProviderOptions options,
            OpenAiTransport transport,
            ObjectMapper mapper
    ) {
        this.options = Objects.requireNonNull(options, "options");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public String id() {
        return options.id();
    }

    @Override
    public String name() {
        return options.name();
    }

    @Override
    public AiProviderApi api() {
        return AiProviderApi.OPENAI_RESPONSES;
    }

    @Override
    public AiProviderFeatures features() {
        return options.features();
    }

    @Override
    public List<AiModel> models() {
        return options.models();
    }

    @Override
    public void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) throws Exception {
        AiProviderRequest hooked = options.requestHook().apply(request);
        OpenAiHttpRequest httpRequest = httpRequest(hooked);
        OpenAiStreamNormalizer normalizer = new OpenAiStreamNormalizer(mapper, sink);
        transport.stream(httpRequest, line -> {
            hooked.options().signal().throwIfAborted();
            normalizer.acceptLine(line);
        });
    }

    public ObjectNode toRequestJson(AiProviderRequest request) {
        ObjectNode body = JSON.objectNode();
        body.put("model", request.model().id());
        body.put("stream", true);
        body.set("input", input(request.turn().messages()));
        applyGenerationOptions(body, request.options().generation());
        systemInstructions(request.turn().messages()).ifPresent(instructions -> body.put("instructions", instructions));
        if (shouldSendTools(request)) {
            ArrayNode tools = JSON.arrayNode();
            for (AiToolSpec tool : request.turn().tools()) {
                ObjectNode function = JSON.objectNode()
                        .put("type", "function")
                        .put("name", tool.name())
                        .put("description", tool.description())
                        .put("strict", false);
                function.set("parameters", tool.inputSchema() == null ? JSON.objectNode() : tool.inputSchema());
                tools.add(function);
            }
            body.set("tools", tools);
            if (features().toolChoice() && request.model().features().toolChoice()) {
                body.put("tool_choice", request.options().generation().toolChoice().orElse("auto"));
            }
            if (features().parallelToolCalls() && request.model().features().parallelToolCalls()) {
                body.put("parallel_tool_calls", request.options().generation().parallelToolCalls());
            }
        }
        return body;
    }

    private boolean shouldSendTools(AiProviderRequest request) {
        return !request.turn().tools().isEmpty()
                && features().toolCalling()
                && request.model().features().toolCalling();
    }

    private static void applyGenerationOptions(ObjectNode body, AiGenerationOptions options) {
        options.maxOutputTokens().ifPresent(value -> body.put("max_output_tokens", value));
        options.temperature().ifPresent(value -> body.put("temperature", value));
        options.topP().ifPresent(value -> body.put("top_p", value));
        if (!options.metadata().isEmpty()) {
            ObjectNode metadata = JSON.objectNode();
            options.metadata().forEach(metadata::put);
            body.set("metadata", metadata);
        }
    }

    private OpenAiHttpRequest httpRequest(AiProviderRequest request) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "text/event-stream");
        headers.putAll(options.defaultHeaders());
        headers.putAll(request.options().headers());
        request.context().auth().headers().forEach(headers::put);
        Optional<String> apiKey = request.context().auth().apiKey()
                .or(() -> Optional.ofNullable(System.getenv("OPENAI_API_KEY")));
        apiKey.ifPresent(value -> headers.putIfAbsent("Authorization", "Bearer " + value));
        return new OpenAiHttpRequest(
                request.context().auth().baseUrl()
                        .map(baseUrl -> baseUrl.endsWith("/responses") ? baseUrl : stripTrailingSlash(baseUrl) + "/responses")
                        .map(java.net.URI::create)
                        .orElse(options.endpoint()),
                headers,
                mapper.writeValueAsString(toRequestJson(request)),
                request.options().timeout());
    }

    private static ArrayNode input(List<AiMessage> messages) {
        ArrayNode input = JSON.arrayNode();
        for (AiMessage message : messages) {
            switch (message) {
                case AiSystemMessage ignored -> {
                }
                case AiUserMessage user -> input.add(message("user", user.content()));
                case AiAssistantMessage assistant -> input.add(message("assistant", assistant.content()));
                case AiToolResultMessage toolResult -> {
                    ObjectNode node = JSON.objectNode()
                            .put("type", "function_call_output")
                            .put("call_id", toolResult.toolCallId())
                            .put("output", textContent(toolResult.content()));
                    if (toolResult.error()) {
                        node.put("status", "incomplete");
                    }
                    input.add(node);
                }
            }
        }
        return input;
    }

    private static ObjectNode message(String role, List<AiContentBlock> content) {
        return JSON.objectNode()
                .put("type", "message")
                .put("role", role)
                .set("content", openAiContent(content));
    }

    private static ArrayNode openAiContent(List<AiContentBlock> blocks) {
        ArrayNode content = JSON.arrayNode();
        for (AiContentBlock block : blocks) {
            switch (block) {
                case AiTextContent text -> content.add(JSON.objectNode()
                        .put("type", "input_text")
                        .put("text", text.text()));
                case AiImageContent image -> content.add(JSON.objectNode()
                        .put("type", "input_image")
                        .put("image_url", "data:" + image.mimeType() + ";base64," + image.data()));
                case AiThinkingContent thinking -> content.add(JSON.objectNode()
                        .put("type", "input_text")
                        .put("text", thinking.thinking()));
                case AiToolCallContent toolCall -> content.add(JSON.objectNode()
                        .put("type", "output_text")
                        .put("text", toolCall.name() + "(" + toolCall.arguments() + ")"));
            }
        }
        return content;
    }

    private static Optional<String> systemInstructions(List<AiMessage> messages) {
        String instructions = messages.stream()
                .filter(AiSystemMessage.class::isInstance)
                .map(AiSystemMessage.class::cast)
                .map(AiSystemMessage::content)
                .filter(content -> !content.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return Optional.of(instructions).filter(value -> !value.isBlank());
    }

    private static String textContent(List<AiContentBlock> blocks) {
        StringBuilder builder = new StringBuilder();
        for (AiContentBlock block : blocks) {
            if (block instanceof AiTextContent text) {
                builder.append(text.text());
            } else {
                builder.append(block);
            }
        }
        return builder.toString();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static final class OpenAiStreamNormalizer {
        private final ObjectMapper mapper;
        private final Consumer<AiStreamEvent> sink;
        private final List<AiContentBlock> content = new ArrayList<>();
        private final Map<Integer, OutputSlot> outputSlots = new LinkedHashMap<>();
        private String messageId;
        private boolean started;

        private OpenAiStreamNormalizer(ObjectMapper mapper, Consumer<AiStreamEvent> sink) {
            this.mapper = mapper;
            this.sink = sink;
        }

        private void acceptLine(String line) {
            if (line == null || line.isBlank() || !line.startsWith("data:")) {
                return;
            }
            String data = line.substring("data:".length()).trim();
            if (data.equals("[DONE]")) {
                return;
            }
            try {
                acceptEvent(mapper.readTree(data));
            } catch (IOException e) {
                throw new IllegalArgumentException("failed to parse OpenAI stream event", e);
            }
        }

        private void acceptEvent(JsonNode event) {
            String type = event.path("type").asText();
            switch (type) {
                case "response.created" -> ensureStarted(event.path("response").path("id").asText("response"));
                case "response.output_item.added" -> outputItemAdded(event);
                case "response.content_part.added" -> contentPartAdded(event);
                case "response.output_text.delta", "response.refusal.delta" -> textDelta(event);
                case "response.output_text.done", "response.output_item.done" -> outputItemDone(event);
                case "response.reasoning_text.delta", "response.reasoning_summary_text.delta" -> thinkingDelta(event);
                case "response.reasoning_text.done", "response.reasoning_summary_text.done" -> thinkingDone(event);
                case "response.function_call_arguments.delta" -> functionCallDelta(event);
                case "response.function_call_arguments.done" -> functionCallDone(event);
                case "response.completed" -> completed(event);
                case "response.failed", "response.incomplete", "error" -> error(event);
                default -> {
                }
            }
        }

        private void outputItemAdded(JsonNode event) {
            JsonNode item = event.path("item");
            String itemType = item.path("type").asText();
            if (itemType.equals("message")) {
                ensureStarted(item.path("id").asText(event.path("item_id").asText("response")));
                createTextSlot(event.path("output_index").asInt(), item.path("id").asText());
            } else if (itemType.equals("function_call")) {
                ensureStarted(event.path("item_id").asText(item.path("id").asText("response")));
                String itemId = item.path("id").asText(event.path("item_id").asText());
                String callId = item.path("call_id").asText(itemId);
                String name = item.path("name").asText();
                ToolCallSlot slot = createToolCallSlot(event.path("output_index").asInt(), callId, name);
                sink.accept(new AiStreamEvent.ToolCallStarted(messageId, slot.contentIndex, callId, name));
            }
        }

        private void contentPartAdded(JsonNode event) {
            ensureStarted(event.path("item_id").asText("response"));
            JsonNode part = event.path("part");
            String partType = part.path("type").asText();
            int index = event.path("content_index").asInt();
            if (partType.equals("output_text")) {
                TextSlot slot = createTextSlot(event.path("output_index").asInt(index), event.path("item_id").asText());
                sink.accept(new AiStreamEvent.TextStarted(messageId, slot.contentIndex));
            } else if (partType.equals("reasoning_text")) {
                ThinkingSlot slot = createThinkingSlot(event.path("output_index").asInt(index));
                sink.accept(new AiStreamEvent.ThinkingStarted(messageId, slot.contentIndex));
            }
        }

        private void textDelta(JsonNode event) {
            ensureStarted(event.path("item_id").asText("response"));
            TextSlot slot = textSlot(event);
            String delta = event.path("delta").asText();
            slot.text.append(delta);
            replaceContent(slot.contentIndex, new AiTextContent(slot.text.toString()));
            sink.accept(new AiStreamEvent.TextDelta(messageId, slot.contentIndex, delta));
        }

        private void thinkingDelta(JsonNode event) {
            ensureStarted(event.path("item_id").asText("response"));
            ThinkingSlot slot = thinkingSlot(event);
            String delta = event.path("delta").asText();
            slot.thinking.append(delta);
            replaceContent(slot.contentIndex, new AiThinkingContent(slot.thinking.toString(), null, false));
            sink.accept(new AiStreamEvent.ThinkingDelta(messageId, slot.contentIndex, delta));
        }

        private void thinkingDone(JsonNode event) {
            ensureStarted(event.path("item_id").asText("response"));
            ThinkingSlot slot = thinkingSlot(event);
            String text = event.path("text").asText(slot.thinking.toString());
            slot.thinking.setLength(0);
            slot.thinking.append(text);
            replaceContent(slot.contentIndex, new AiThinkingContent(text, null, false));
            sink.accept(new AiStreamEvent.ThinkingEnded(messageId, slot.contentIndex));
        }

        private void outputItemDone(JsonNode event) {
            ensureStarted(event.path("item_id").asText("response"));
            JsonNode item = event.path("item");
            if (item.isObject()) {
                if (item.path("type").asText().equals("message")) {
                    TextSlot slot = textSlot(event);
                    String text = textFromOutputMessage(item).orElse(slot.text.toString());
                    slot.text.setLength(0);
                    slot.text.append(text);
                    replaceContent(slot.contentIndex, new AiTextContent(text));
                    sink.accept(new AiStreamEvent.TextEnded(messageId, slot.contentIndex));
                    return;
                }
                if (item.path("type").asText().equals("function_call")) {
                    functionCallDoneFromItem(event);
                    return;
                }
            }
            if (event.path("type").asText().equals("response.output_text.done")) {
                TextSlot slot = textSlot(event);
                String text = event.path("text").asText(slot.text.toString());
                slot.text.setLength(0);
                slot.text.append(text);
                replaceContent(slot.contentIndex, new AiTextContent(text));
                sink.accept(new AiStreamEvent.TextEnded(messageId, slot.contentIndex));
            }
        }

        private void functionCallDelta(JsonNode event) {
            ensureStarted(event.path("item_id").asText("response"));
            ToolCallSlot slot = toolCallSlot(event);
            String delta = event.path("delta").asText();
            slot.arguments.append(delta);
            replaceContent(slot.contentIndex, new AiToolCallContent(slot.callId, slot.name, parseArguments(slot.arguments.toString())));
            sink.accept(new AiStreamEvent.ToolCallDelta(
                    messageId,
                    slot.contentIndex,
                    JSON.textNode(delta)));
        }

        private void functionCallDone(JsonNode event) {
            ensureStarted(event.path("item_id").asText("response"));
            ToolCallSlot slot = toolCallSlot(event);
            slot.name = event.path("name").asText(slot.name);
            slot.arguments.setLength(0);
            slot.arguments.append(event.path("arguments").asText("{}"));
            replaceContent(slot.contentIndex, new AiToolCallContent(slot.callId, slot.name, parseArguments(slot.arguments.toString())));
            sink.accept(new AiStreamEvent.ToolCallEnded(messageId, slot.contentIndex, slot.callId));
        }

        private void functionCallDoneFromItem(JsonNode event) {
            JsonNode item = event.path("item");
            ToolCallSlot slot = toolCallSlot(event.path("output_index").asInt(), item);
            slot.name = item.path("name").asText(slot.name);
            slot.arguments.setLength(0);
            slot.arguments.append(item.path("arguments").asText(slot.arguments.toString()));
            replaceContent(slot.contentIndex, new AiToolCallContent(slot.callId, slot.name, parseArguments(slot.arguments.toString())));
            sink.accept(new AiStreamEvent.ToolCallEnded(messageId, slot.contentIndex, slot.callId));
        }

        private void completed(JsonNode event) {
            ensureStarted(event.path("response").path("id").asText("response"));
            JsonNode response = event.path("response");
            sink.accept(new AiStreamEvent.MessageCompleted(
                    messageId,
                    new AiAssistantMessage(
                            content,
                            stopReason(response.path("status").asText("completed"), content),
                            usage(response.path("usage")))));
        }

        private void error(JsonNode event) {
            ensureStarted(event.path("response").path("id").asText(event.path("item_id").asText("response")));
            String message = event.path("message").asText(event.path("response").path("error").path("message").asText("OpenAI stream error"));
            sink.accept(new AiStreamEvent.MessageErrored(messageId, message));
        }

        private void ensureStarted(String id) {
            if (!started) {
                messageId = id == null || id.isBlank() ? "response" : id;
                started = true;
                sink.accept(new AiStreamEvent.MessageStarted(messageId));
            }
        }

        private TextSlot textSlot(JsonNode event) {
            return createTextSlot(event.path("output_index").asInt(event.path("content_index").asInt()), event.path("item_id").asText());
        }

        private ThinkingSlot thinkingSlot(JsonNode event) {
            return createThinkingSlot(event.path("output_index").asInt(event.path("content_index").asInt()));
        }

        private ToolCallSlot toolCallSlot(JsonNode event) {
            return toolCallSlot(event.path("output_index").asInt(), event);
        }

        private ToolCallSlot toolCallSlot(int outputIndex, JsonNode event) {
            OutputSlot existing = outputSlots.get(outputIndex);
            if (existing instanceof ToolCallSlot slot) {
                return slot;
            }
            String itemId = event.path("item_id").asText(event.path("id").asText("call-" + outputIndex));
            String callId = event.path("call_id").asText(itemId);
            String name = event.path("name").asText("");
            return createToolCallSlot(outputIndex, callId, name);
        }

        private TextSlot createTextSlot(int outputIndex, String itemId) {
            OutputSlot existing = outputSlots.get(outputIndex);
            if (existing instanceof TextSlot slot) {
                return slot;
            }
            TextSlot slot = new TextSlot(content.size(), itemId == null ? "" : itemId, new StringBuilder());
            outputSlots.put(outputIndex, slot);
            content.add(new AiTextContent(""));
            return slot;
        }

        private ThinkingSlot createThinkingSlot(int outputIndex) {
            OutputSlot existing = outputSlots.get(outputIndex);
            if (existing instanceof ThinkingSlot slot) {
                return slot;
            }
            ThinkingSlot slot = new ThinkingSlot(content.size(), new StringBuilder());
            outputSlots.put(outputIndex, slot);
            content.add(new AiThinkingContent("", null, false));
            return slot;
        }

        private ToolCallSlot createToolCallSlot(int outputIndex, String callId, String name) {
            OutputSlot existing = outputSlots.get(outputIndex);
            if (existing instanceof ToolCallSlot slot) {
                return slot;
            }
            ToolCallSlot slot = new ToolCallSlot(content.size(), callId, name, new StringBuilder());
            outputSlots.put(outputIndex, slot);
            content.add(new AiToolCallContent(callId, name, JSON.objectNode()));
            return slot;
        }

        private void replaceContent(int index, AiContentBlock block) {
            content.set(index, block);
        }

        private static Optional<String> textFromOutputMessage(JsonNode item) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                return Optional.empty();
            }
            StringBuilder builder = new StringBuilder();
            for (JsonNode part : content) {
                String type = part.path("type").asText();
                if (type.equals("output_text")) {
                    builder.append(part.path("text").asText());
                } else if (type.equals("refusal")) {
                    builder.append(part.path("refusal").asText());
                }
            }
            return Optional.of(builder.toString()).filter(value -> !value.isEmpty());
        }

        private JsonNode parseArguments(String raw) {
            try {
                return mapper.readTree(raw);
            } catch (IOException e) {
                return JSON.objectNode().put("raw", raw);
            }
        }

        private static AiStopReason stopReason(String status, List<AiContentBlock> content) {
            if (content.stream().anyMatch(AiToolCallContent.class::isInstance)) {
                return AiStopReason.TOOL_USE;
            }
            return switch (status) {
                case "incomplete" -> AiStopReason.LENGTH;
                case "failed" -> AiStopReason.ERROR;
                default -> AiStopReason.STOP;
            };
        }

        private static AiUsage usage(JsonNode usage) {
            if (usage == null || usage.isMissingNode()) {
                return AiUsage.zero();
            }
            return new AiUsage(
                    usage.path("input_tokens").asLong(0),
                    usage.path("output_tokens").asLong(0),
                    usage.path("input_tokens_details").path("cached_tokens").asLong(0),
                    usage.path("output_tokens_details").path("reasoning_tokens").asLong(0));
        }
    }

    private sealed interface OutputSlot permits TextSlot, ThinkingSlot, ToolCallSlot {
        int contentIndex();
    }

    private record TextSlot(int contentIndex, String itemId, StringBuilder text) implements OutputSlot {
    }

    private record ThinkingSlot(int contentIndex, StringBuilder thinking) implements OutputSlot {
    }

    private static final class ToolCallSlot implements OutputSlot {
        private final int contentIndex;
        private final String callId;
        private String name;
        private final StringBuilder arguments;

        private ToolCallSlot(int contentIndex, String callId, String name, StringBuilder arguments) {
            this.contentIndex = contentIndex;
            this.callId = callId;
            this.name = name;
            this.arguments = arguments;
        }

        @Override
        public int contentIndex() {
            return contentIndex;
        }
    }
}
