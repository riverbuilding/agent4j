package com.agent4j.ai.anthropic;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiContentBlock;
import com.agent4j.ai.AiImageContent;
import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderApi;
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
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class AnthropicMessagesProvider implements AiProvider {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final AnthropicMessagesProviderOptions options;
    private final AnthropicTransport transport;
    private final ObjectMapper mapper;

    public AnthropicMessagesProvider(List<AiModel> models) {
        this(AnthropicMessagesProviderOptions.defaults(models), new DefaultAnthropicTransport(), new ObjectMapper());
    }

    public AnthropicMessagesProvider(AnthropicMessagesProviderOptions options, AnthropicTransport transport) {
        this(options, transport, new ObjectMapper());
    }

    public AnthropicMessagesProvider(
            AnthropicMessagesProviderOptions options,
            AnthropicTransport transport,
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
        return AiProviderApi.ANTHROPIC_MESSAGES;
    }

    @Override
    public List<AiModel> models() {
        return options.models();
    }

    @Override
    public void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) throws Exception {
        AiProviderRequest hooked = options.requestHook().apply(request);
        AnthropicHttpRequest httpRequest = httpRequest(hooked);
        AnthropicStreamNormalizer normalizer = new AnthropicStreamNormalizer(mapper, sink);
        transport.stream(httpRequest, line -> {
            hooked.options().signal().throwIfAborted();
            normalizer.acceptLine(line);
        });
        normalizer.finish();
    }

    public ObjectNode toRequestJson(AiProviderRequest request) {
        ObjectNode body = JSON.objectNode();
        body.put("model", request.model().id());
        body.put("stream", true);
        body.put("max_tokens", request.model().maxTokens());
        systemPrompt(request.turn().messages()).ifPresent(system -> body.put("system", system));
        body.set("messages", messages(request.turn().messages()));
        if (!request.turn().tools().isEmpty()) {
            body.set("tools", tools(request.turn().tools()));
        }
        return body;
    }

    private AnthropicHttpRequest httpRequest(AiProviderRequest request) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "text/event-stream");
        headers.put("anthropic-version", options.anthropicVersion());
        headers.putAll(options.defaultHeaders());
        headers.putAll(request.options().headers());
        request.context().auth().headers().forEach(headers::put);
        Optional<String> apiKey = request.context().auth().apiKey()
                .or(() -> Optional.ofNullable(System.getenv("ANTHROPIC_API_KEY")));
        apiKey.ifPresent(value -> headers.putIfAbsent("x-api-key", value));
        return new AnthropicHttpRequest(
                endpoint(request),
                headers,
                mapper.writeValueAsString(toRequestJson(request)),
                request.options().timeout());
    }

    private URI endpoint(AiProviderRequest request) {
        return request.context().auth().baseUrl()
                .map(baseUrl -> baseUrl.endsWith("/messages") ? baseUrl : stripTrailingSlash(baseUrl) + "/messages")
                .map(URI::create)
                .orElse(options.endpoint());
    }

    private static ArrayNode messages(List<AiMessage> messages) {
        ArrayNode output = JSON.arrayNode();
        for (AiMessage message : messages) {
            switch (message) {
                case AiSystemMessage ignored -> {
                }
                case AiUserMessage user -> output.add(message("user", anthropicContent(user.content())));
                case AiAssistantMessage assistant -> output.add(message("assistant", anthropicContent(assistant.content())));
                case AiToolResultMessage toolResult -> output.add(message("user", toolResultContent(toolResult)));
            }
        }
        return output;
    }

    private static ObjectNode message(String role, ArrayNode content) {
        ObjectNode message = JSON.objectNode();
        message.put("role", role);
        message.set("content", content);
        return message;
    }

    private static ArrayNode anthropicContent(List<AiContentBlock> blocks) {
        ArrayNode content = JSON.arrayNode();
        for (AiContentBlock block : blocks) {
            switch (block) {
                case AiTextContent text -> content.add(JSON.objectNode()
                        .put("type", "text")
                        .put("text", text.text()));
                case AiImageContent image -> {
                    ObjectNode source = JSON.objectNode()
                            .put("type", "base64")
                            .put("media_type", image.mimeType())
                            .put("data", image.data());
                    ObjectNode imageBlock = JSON.objectNode().put("type", "image");
                    imageBlock.set("source", source);
                    content.add(imageBlock);
                }
                case AiThinkingContent thinking -> {
                    ObjectNode thinkingBlock = JSON.objectNode()
                            .put("type", thinking.redacted() ? "redacted_thinking" : "thinking")
                            .put("thinking", thinking.thinking());
                    if (thinking.thinkingSignature() != null && !thinking.thinkingSignature().isBlank()) {
                        thinkingBlock.put("signature", thinking.thinkingSignature());
                    }
                    content.add(thinkingBlock);
                }
                case AiToolCallContent toolCall -> {
                    ObjectNode toolUse = JSON.objectNode()
                            .put("type", "tool_use")
                            .put("id", toolCall.id())
                            .put("name", toolCall.name());
                    toolUse.set("input", toolCall.arguments() == null ? JSON.objectNode() : toolCall.arguments());
                    content.add(toolUse);
                }
            }
        }
        return content;
    }

    private static ArrayNode toolResultContent(AiToolResultMessage toolResult) {
        ObjectNode result = JSON.objectNode()
                .put("type", "tool_result")
                .put("tool_use_id", toolResult.toolCallId())
                .put("content", textContent(toolResult.content()));
        if (toolResult.error()) {
            result.put("is_error", true);
        }
        return JSON.arrayNode().add(result);
    }

    private static ArrayNode tools(List<AiToolSpec> specs) {
        ArrayNode tools = JSON.arrayNode();
        for (AiToolSpec spec : specs) {
            ObjectNode tool = JSON.objectNode()
                    .put("name", spec.name())
                    .put("description", spec.description());
            tool.set("input_schema", spec.inputSchema() == null ? JSON.objectNode() : spec.inputSchema());
            tools.add(tool);
        }
        return tools;
    }

    private static Optional<String> systemPrompt(List<AiMessage> messages) {
        String system = messages.stream()
                .filter(AiSystemMessage.class::isInstance)
                .map(AiSystemMessage.class::cast)
                .map(AiSystemMessage::content)
                .filter(content -> !content.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return Optional.of(system).filter(value -> !value.isBlank());
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

    private static final class AnthropicStreamNormalizer {
        private final ObjectMapper mapper;
        private final Consumer<AiStreamEvent> sink;
        private final List<AiContentBlock> content = new ArrayList<>();
        private final Map<Integer, ContentSlot> contentSlots = new LinkedHashMap<>();
        private final StringBuilder data = new StringBuilder();
        private String messageId;
        private boolean started;
        private AiStopReason stopReason = AiStopReason.STOP;
        private long inputTokens;
        private long outputTokens;
        private long cachedInputTokens;

        private AnthropicStreamNormalizer(ObjectMapper mapper, Consumer<AiStreamEvent> sink) {
            this.mapper = mapper;
            this.sink = sink;
        }

        private void acceptLine(String line) {
            if (line == null) {
                return;
            }
            if (line.isBlank()) {
                flush();
                return;
            }
            if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).trim());
            }
        }

        private void finish() {
            flush();
        }

        private void flush() {
            if (data.isEmpty()) {
                return;
            }
            String payload = data.toString();
            data.setLength(0);
            try {
                acceptEvent(mapper.readTree(payload));
            } catch (IOException e) {
                throw new IllegalArgumentException("failed to parse Anthropic stream event", e);
            }
        }

        private void acceptEvent(JsonNode event) {
            String type = event.path("type").asText();
            switch (type) {
                case "message_start" -> messageStart(event);
                case "content_block_start" -> contentBlockStart(event);
                case "content_block_delta" -> contentBlockDelta(event);
                case "content_block_stop" -> contentBlockStop(event);
                case "message_delta" -> messageDelta(event);
                case "message_stop" -> messageStop();
                case "error" -> error(event);
                default -> {
                }
            }
        }

        private void messageStart(JsonNode event) {
            JsonNode message = event.path("message");
            ensureStarted(message.path("id").asText("message"));
            updateUsage(message.path("usage"));
        }

        private void contentBlockStart(JsonNode event) {
            ensureStarted("message");
            int index = event.path("index").asInt();
            JsonNode block = event.path("content_block");
            String type = block.path("type").asText();
            if (type.equals("text")) {
                TextSlot slot = createTextSlot(index, block.path("text").asText(""));
                sink.accept(new AiStreamEvent.TextStarted(messageId, slot.contentIndex));
            } else if (type.equals("tool_use")) {
                ToolCallSlot slot = createToolCallSlot(
                        index,
                        block.path("id").asText("tool-" + index),
                        block.path("name").asText(""),
                        initialInput(block.path("input")));
                sink.accept(new AiStreamEvent.ToolCallStarted(messageId, slot.contentIndex, slot.toolCallId, slot.name));
            } else if (type.equals("thinking")) {
                ThinkingSlot slot = createThinkingSlot(index, block.path("thinking").asText(""));
                sink.accept(new AiStreamEvent.ThinkingStarted(messageId, slot.contentIndex));
            }
        }

        private void contentBlockDelta(JsonNode event) {
            ensureStarted("message");
            int index = event.path("index").asInt();
            JsonNode delta = event.path("delta");
            switch (delta.path("type").asText()) {
                case "text_delta" -> textDelta(index, delta.path("text").asText());
                case "input_json_delta" -> toolCallDelta(index, delta.path("partial_json").asText());
                case "thinking_delta" -> thinkingDelta(index, delta.path("thinking").asText());
                case "signature_delta" -> thinkingSignatureDelta(index, delta.path("signature").asText());
                default -> {
                }
            }
        }

        private void contentBlockStop(JsonNode event) {
            ensureStarted("message");
            int index = event.path("index").asInt();
            ContentSlot slot = contentSlots.get(index);
            if (slot instanceof TextSlot text) {
                sink.accept(new AiStreamEvent.TextEnded(messageId, text.contentIndex));
            } else if (slot instanceof ToolCallSlot toolCall) {
                replaceContent(toolCall.contentIndex, new AiToolCallContent(
                        toolCall.toolCallId,
                        toolCall.name,
                        parseArguments(toolCall.arguments.toString())));
                sink.accept(new AiStreamEvent.ToolCallEnded(messageId, toolCall.contentIndex, toolCall.toolCallId));
            } else if (slot instanceof ThinkingSlot thinking) {
                sink.accept(new AiStreamEvent.ThinkingEnded(messageId, thinking.contentIndex));
            }
        }

        private void messageDelta(JsonNode event) {
            JsonNode delta = event.path("delta");
            if (delta.has("stop_reason")) {
                stopReason = stopReason(delta.path("stop_reason").asText());
            }
            updateUsage(event.path("usage"));
        }

        private void messageStop() {
            ensureStarted("message");
            sink.accept(new AiStreamEvent.MessageCompleted(
                    messageId,
                    new AiAssistantMessage(
                            content,
                            stopReason,
                            new AiUsage(inputTokens, outputTokens, cachedInputTokens, 0))));
        }

        private void error(JsonNode event) {
            ensureStarted("message");
            String message = event.path("error").path("message").asText("Anthropic stream error");
            sink.accept(new AiStreamEvent.MessageErrored(messageId, message));
        }

        private void textDelta(int index, String delta) {
            TextSlot slot = textSlot(index);
            slot.text.append(delta);
            replaceContent(slot.contentIndex, new AiTextContent(slot.text.toString()));
            sink.accept(new AiStreamEvent.TextDelta(messageId, slot.contentIndex, delta));
        }

        private void toolCallDelta(int index, String delta) {
            ToolCallSlot slot = toolCallSlot(index);
            slot.arguments.append(delta);
            replaceContent(slot.contentIndex, new AiToolCallContent(
                    slot.toolCallId,
                    slot.name,
                    parseArguments(slot.arguments.toString())));
            sink.accept(new AiStreamEvent.ToolCallDelta(messageId, slot.contentIndex, JSON.textNode(delta)));
        }

        private void thinkingDelta(int index, String delta) {
            ThinkingSlot slot = thinkingSlot(index);
            slot.thinking.append(delta);
            String signature = slot.signature.isEmpty() ? null : slot.signature.toString();
            replaceContent(slot.contentIndex, new AiThinkingContent(slot.thinking.toString(), signature, false));
            sink.accept(new AiStreamEvent.ThinkingDelta(messageId, slot.contentIndex, delta));
        }

        private void thinkingSignatureDelta(int index, String signature) {
            ThinkingSlot slot = thinkingSlot(index);
            slot.signature.setLength(0);
            slot.signature.append(signature);
            replaceContent(slot.contentIndex, new AiThinkingContent(slot.thinking.toString(), signature, false));
        }

        private void ensureStarted(String id) {
            if (!started) {
                messageId = id == null || id.isBlank() ? "message" : id;
                started = true;
                sink.accept(new AiStreamEvent.MessageStarted(messageId));
            }
        }

        private TextSlot textSlot(int index) {
            ContentSlot existing = contentSlots.get(index);
            if (existing instanceof TextSlot slot) {
                return slot;
            }
            return createTextSlot(index, "");
        }

        private ThinkingSlot thinkingSlot(int index) {
            ContentSlot existing = contentSlots.get(index);
            if (existing instanceof ThinkingSlot slot) {
                return slot;
            }
            return createThinkingSlot(index, "");
        }

        private ToolCallSlot toolCallSlot(int index) {
            ContentSlot existing = contentSlots.get(index);
            if (existing instanceof ToolCallSlot slot) {
                return slot;
            }
            return createToolCallSlot(index, "tool-" + index, "", "");
        }

        private TextSlot createTextSlot(int index, String initialText) {
            ContentSlot existing = contentSlots.get(index);
            if (existing instanceof TextSlot slot) {
                return slot;
            }
            TextSlot slot = new TextSlot(content.size(), new StringBuilder(initialText));
            contentSlots.put(index, slot);
            content.add(new AiTextContent(initialText));
            return slot;
        }

        private ThinkingSlot createThinkingSlot(int index, String initialThinking) {
            ContentSlot existing = contentSlots.get(index);
            if (existing instanceof ThinkingSlot slot) {
                return slot;
            }
            ThinkingSlot slot = new ThinkingSlot(content.size(), new StringBuilder(initialThinking), new StringBuilder());
            contentSlots.put(index, slot);
            content.add(new AiThinkingContent(initialThinking, null, false));
            return slot;
        }

        private ToolCallSlot createToolCallSlot(int index, String toolCallId, String name, String initialInput) {
            ContentSlot existing = contentSlots.get(index);
            if (existing instanceof ToolCallSlot slot) {
                return slot;
            }
            ToolCallSlot slot = new ToolCallSlot(content.size(), toolCallId, name, new StringBuilder(initialInput));
            contentSlots.put(index, slot);
            content.add(new AiToolCallContent(toolCallId, name, parseArguments(initialInput)));
            return slot;
        }

        private void updateUsage(JsonNode usage) {
            if (usage == null || usage.isMissingNode()) {
                return;
            }
            if (usage.has("input_tokens")) {
                inputTokens = usage.path("input_tokens").asLong(0);
            }
            if (usage.has("output_tokens")) {
                outputTokens = usage.path("output_tokens").asLong(0);
            }
            if (usage.has("cache_read_input_tokens")) {
                cachedInputTokens = usage.path("cache_read_input_tokens").asLong(0);
            }
        }

        private void replaceContent(int index, AiContentBlock block) {
            content.set(index, block);
        }

        private String initialInput(JsonNode input) {
            if (input == null || input.isMissingNode() || input.isNull() || input.isEmpty()) {
                return "";
            }
            try {
                return mapper.writeValueAsString(input);
            } catch (IOException e) {
                return "";
            }
        }

        private JsonNode parseArguments(String raw) {
            if (raw == null || raw.isBlank()) {
                return JSON.objectNode();
            }
            try {
                return mapper.readTree(raw);
            } catch (IOException e) {
                return JSON.objectNode().put("raw", raw);
            }
        }

        private static AiStopReason stopReason(String reason) {
            return switch (reason) {
                case "tool_use" -> AiStopReason.TOOL_USE;
                case "max_tokens" -> AiStopReason.LENGTH;
                case "error" -> AiStopReason.ERROR;
                default -> AiStopReason.STOP;
            };
        }
    }

    private sealed interface ContentSlot permits TextSlot, ThinkingSlot, ToolCallSlot {
        int contentIndex();
    }

    private record TextSlot(int contentIndex, StringBuilder text) implements ContentSlot {
    }

    private record ThinkingSlot(int contentIndex, StringBuilder thinking, StringBuilder signature) implements ContentSlot {
    }

    private static final class ToolCallSlot implements ContentSlot {
        private final int contentIndex;
        private final String toolCallId;
        private final String name;
        private final StringBuilder arguments;

        private ToolCallSlot(int contentIndex, String toolCallId, String name, StringBuilder arguments) {
            this.contentIndex = contentIndex;
            this.toolCallId = toolCallId;
            this.name = name;
            this.arguments = arguments;
        }

        @Override
        public int contentIndex() {
            return contentIndex;
        }
    }
}
