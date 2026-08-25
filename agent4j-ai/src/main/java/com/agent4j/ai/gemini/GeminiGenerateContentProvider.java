package com.agent4j.ai.gemini;

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

/** Gemini GenerateContent API provider with Server-Sent Events streaming. */
public final class GeminiGenerateContentProvider implements AiProvider {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final URI DEFAULT_ENDPOINT = URI.create("https://generativelanguage.googleapis.com/v1beta");

    private final List<AiModel> models;
    private final GeminiTransport transport;
    private final ObjectMapper mapper;

    public GeminiGenerateContentProvider(List<AiModel> models) {
        this(models, new DefaultGeminiTransport());
    }

    public GeminiGenerateContentProvider(List<AiModel> models, GeminiTransport transport) {
        this.models = List.copyOf(Objects.requireNonNull(models, "models"));
        this.transport = Objects.requireNonNull(transport, "transport");
        this.mapper = new ObjectMapper();
    }

    @Override public String id() { return "gemini"; }
    @Override public String name() { return "Google Gemini"; }
    @Override public AiProviderApi api() { return AiProviderApi.GOOGLE_GENERATIVE_AI; }
    @Override public AiProviderFeatures features() { return AiProviderFeatures.withoutParallelToolCalls(); }
    @Override public List<AiModel> models() { return models; }

    @Override
    public void stream(AiProviderRequest request, Consumer<AiStreamEvent> sink) throws Exception {
        GeminiNormalizer normalizer = new GeminiNormalizer(mapper, sink);
        transport.stream(httpRequest(request), line -> {
            request.options().signal().throwIfAborted();
            normalizer.acceptLine(line);
        });
        normalizer.finish();
    }

    public ObjectNode toRequestJson(AiProviderRequest request) {
        ObjectNode body = JSON.objectNode();
        systemInstruction(request.turn().messages()).ifPresent(value -> body.set("systemInstruction", content("user", List.of(JSON.objectNode().put("text", value)))));
        body.set("contents", contents(request.turn().messages()));
        if (!request.turn().tools().isEmpty() && request.model().features().toolCalling()) {
            ArrayNode declarations = JSON.arrayNode();
            request.turn().tools().forEach(spec -> declarations.add(functionDeclaration(spec)));
            body.set("tools", JSON.arrayNode().add(JSON.objectNode().set("functionDeclarations", declarations)));
        }
        AiGenerationOptions generation = request.options().generation();
        ObjectNode config = JSON.objectNode();
        generation.maxOutputTokens().ifPresent(value -> config.put("maxOutputTokens", value));
        generation.temperature().ifPresent(value -> config.put("temperature", value));
        generation.topP().ifPresent(value -> config.put("topP", value));
        generation.topK().ifPresent(value -> config.put("topK", value));
        if (!config.isEmpty()) body.set("generationConfig", config);
        return body;
    }

    private GeminiHttpRequest httpRequest(AiProviderRequest request) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "text/event-stream");
        headers.putAll(request.options().headers());
        request.context().auth().headers().forEach(headers::put);
        request.context().auth().apiKey().or(() -> Optional.ofNullable(System.getenv("GEMINI_API_KEY")))
                .ifPresent(key -> headers.putIfAbsent("x-goog-api-key", key));
        return new GeminiHttpRequest(endpoint(request.model()), headers, mapper.writeValueAsString(toRequestJson(request)), request.options().timeout());
    }

    private static URI endpoint(AiModel model) {
        String base = model.baseUrl().orElse(DEFAULT_ENDPOINT.toString()).replaceAll("/+$", "");
        return URI.create(base + "/models/" + model.id() + ":streamGenerateContent?alt=sse");
    }

    private static ArrayNode contents(List<AiMessage> messages) {
        ArrayNode output = JSON.arrayNode();
        for (AiMessage message : messages) {
            switch (message) {
                case AiSystemMessage ignored -> { }
                case AiUserMessage user -> output.add(content("user", parts(user.content())));
                case AiAssistantMessage assistant -> output.add(content("model", parts(assistant.content())));
                case AiToolResultMessage result -> output.add(content("user", List.of(JSON.objectNode().set("functionResponse", JSON.objectNode()
                        .put("name", result.toolName()).set("response", JSON.objectNode().put("result", text(result.content())))))));
            }
        }
        return output;
    }

    private static ArrayNode parts(List<AiContentBlock> blocks) {
        ArrayNode output = JSON.arrayNode();
        for (AiContentBlock block : blocks) {
            switch (block) {
                case AiTextContent text -> output.add(JSON.objectNode().put("text", text.text()));
                case AiImageContent image -> output.add(JSON.objectNode().set("inlineData", JSON.objectNode().put("mimeType", image.mimeType()).put("data", image.data())));
                case AiToolCallContent call -> output.add(JSON.objectNode().set("functionCall", JSON.objectNode().put("name", call.name()).set("args", call.arguments())));
                default -> { }
            }
        }
        return output;
    }

    private static ObjectNode content(String role, List<ObjectNode> parts) {
        ObjectNode content = JSON.objectNode().put("role", role);
        ArrayNode output = JSON.arrayNode();
        parts.forEach(output::add);
        return content.set("parts", output);
    }

    private static ObjectNode content(String role, ArrayNode parts) {
        return JSON.objectNode().put("role", role).set("parts", parts);
    }

    private static ObjectNode functionDeclaration(AiToolSpec spec) {
        return JSON.objectNode().put("name", spec.name()).put("description", spec.description())
                .set("parametersJsonSchema", spec.inputSchema() == null ? JSON.objectNode() : spec.inputSchema());
    }

    private static Optional<String> systemInstruction(List<AiMessage> messages) {
        return messages.stream().filter(AiSystemMessage.class::isInstance).map(AiSystemMessage.class::cast)
                .map(AiSystemMessage::content).filter(value -> !value.isBlank()).reduce((left, right) -> left + "\n" + right);
    }

    private static String text(List<AiContentBlock> blocks) {
        return blocks.stream().filter(AiTextContent.class::isInstance).map(AiTextContent.class::cast).map(AiTextContent::text).reduce("", String::concat);
    }

    private static final class GeminiNormalizer {
        private final ObjectMapper mapper;
        private final Consumer<AiStreamEvent> sink;
        private final List<AiContentBlock> content = new ArrayList<>();
        private final StringBuilder data = new StringBuilder();
        private String messageId = "message";
        private boolean started;
        private long inputTokens;
        private long outputTokens;
        private boolean completed;

        private GeminiNormalizer(ObjectMapper mapper, Consumer<AiStreamEvent> sink) { this.mapper = mapper; this.sink = sink; }
        private void acceptLine(String line) {
            if (line == null) return;
            if (line.isBlank()) { flush(); return; }
            if (line.startsWith("data:")) data.append(line.substring(5).trim());
        }
        private void finish() { flush(); if (started && !completed) complete(AiStopReason.STOP); }
        private void flush() {
            if (data.isEmpty()) return;
            String payload = data.toString(); data.setLength(0);
            try { accept(mapper.readTree(payload)); } catch (IOException error) { throw new IllegalArgumentException("failed to parse Gemini stream event", error); }
        }
        private void accept(JsonNode event) {
            JsonNode candidate = event.path("candidates").path(0);
            if (candidate.isMissingNode()) return;
            ensureStarted(candidate.path("index").asText("message"));
            JsonNode parts = candidate.path("content").path("parts");
            for (JsonNode part : parts) {
                if (part.has("text")) text(part.path("text").asText());
                if (part.has("functionCall")) functionCall(part.path("functionCall"));
            }
            JsonNode usage = event.path("usageMetadata");
            inputTokens = usage.path("promptTokenCount").asLong(inputTokens);
            outputTokens = usage.path("candidatesTokenCount").asLong(outputTokens);
            if (candidate.has("finishReason")) complete(stopReason(candidate.path("finishReason").asText()));
        }
        private void ensureStarted(String id) { if (!started) { messageId = id; started = true; sink.accept(new AiStreamEvent.MessageStarted(messageId)); } }
        private void text(String delta) {
            int index = content.size(); content.add(new AiTextContent(delta));
            sink.accept(new AiStreamEvent.TextStarted(messageId, index)); sink.accept(new AiStreamEvent.TextDelta(messageId, index, delta)); sink.accept(new AiStreamEvent.TextEnded(messageId, index));
        }
        private void functionCall(JsonNode call) {
            int index = content.size(); String name = call.path("name").asText(); String id = "gemini-call-" + index;
            JsonNode args = call.has("args") ? call.path("args") : JSON.objectNode(); content.add(new AiToolCallContent(id, name, args));
            sink.accept(new AiStreamEvent.ToolCallStarted(messageId, index, id, name)); sink.accept(new AiStreamEvent.ToolCallDelta(messageId, index, args)); sink.accept(new AiStreamEvent.ToolCallEnded(messageId, index, id));
        }
        private void complete(AiStopReason reason) { if (!completed) { completed = true; sink.accept(new AiStreamEvent.MessageCompleted(messageId, new AiAssistantMessage(content, reason, new AiUsage(inputTokens, outputTokens, 0, 0)))); } }
        private static AiStopReason stopReason(String reason) { return reason.equals("MAX_TOKENS") ? AiStopReason.LENGTH : reason.equals("STOP") ? AiStopReason.STOP : AiStopReason.ERROR; }
    }
}
