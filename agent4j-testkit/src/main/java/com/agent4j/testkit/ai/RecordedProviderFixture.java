package com.agent4j.testkit.ai;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiContentBlock;
import com.agent4j.ai.AiContentBlocks;
import com.agent4j.ai.AiCost;
import com.agent4j.ai.AiInputType;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiModelCompat;
import com.agent4j.ai.AiModelReference;
import com.agent4j.ai.AiProviderApi;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record RecordedProviderFixture(
        String providerId,
        String providerName,
        AiProviderApi api,
        AiModel model,
        List<AiStreamEvent> events
) {
    public RecordedProviderFixture {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(providerName, "providerName");
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(events, "events");
        events = List.copyOf(events);
    }

    public static RecordedProviderFixture read(Path path) throws IOException {
        return read(path, new ObjectMapper());
    }

    public static RecordedProviderFixture read(Path path, ObjectMapper mapper) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(mapper, "mapper");
        JsonNode root = mapper.readTree(path.toFile());
        JsonNode provider = root.path("provider");
        String providerId = requiredText(provider, "id");
        String providerName = text(provider, "name").orElse(providerId);
        AiProviderApi api = text(provider, "api")
                .map(AiProviderApi::fromWireName)
                .orElse(AiProviderApi.CUSTOM);
        AiModel model = model(root.path("model"), providerId);
        List<AiStreamEvent> events = new ArrayList<>();
        root.path("events").forEach(event -> events.add(event(event)));
        return new RecordedProviderFixture(providerId, providerName, api, model, events);
    }

    private static AiModel model(JsonNode node, String providerId) {
        String id = requiredText(node, "id");
        String name = text(node, "name").orElse(id);
        AiProviderApi api = text(node, "api")
                .map(AiProviderApi::fromWireName)
                .orElse(AiProviderApi.CUSTOM);
        EnumSet<AiInputType> input = EnumSet.noneOf(AiInputType.class);
        node.path("input").forEach(value -> {
            if (value.isTextual()) {
                input.add(AiInputType.valueOf(value.asText().toUpperCase()));
            }
        });
        return new AiModel(
                new AiModelReference(providerId, id),
                name,
                Optional.of(api),
                Optional.empty(),
                node.path("reasoning").asBoolean(false),
                Map.of(),
                java.util.Set.of(),
                input,
                node.path("contextWindow").asLong(128000),
                node.path("maxTokens").asLong(16384),
                AiCost.zero(),
                AiModelCompat.defaults());
    }

    private static AiStreamEvent event(JsonNode node) {
        String type = requiredText(node, "type");
        String messageId = requiredText(node, "messageId");
        return switch (type) {
            case "message_start" -> new AiStreamEvent.MessageStarted(messageId);
            case "message_error" -> new AiStreamEvent.MessageErrored(messageId, requiredText(node, "error"));
            case "text_start" -> new AiStreamEvent.TextStarted(messageId, node.path("contentIndex").asInt());
            case "text_delta" -> new AiStreamEvent.TextDelta(
                    messageId,
                    node.path("contentIndex").asInt(),
                    requiredText(node, "delta"));
            case "text_end" -> new AiStreamEvent.TextEnded(messageId, node.path("contentIndex").asInt());
            case "thinking_start" -> new AiStreamEvent.ThinkingStarted(messageId, node.path("contentIndex").asInt());
            case "thinking_delta" -> new AiStreamEvent.ThinkingDelta(
                    messageId,
                    node.path("contentIndex").asInt(),
                    requiredText(node, "delta"));
            case "thinking_end" -> new AiStreamEvent.ThinkingEnded(messageId, node.path("contentIndex").asInt());
            case "toolcall_start" -> new AiStreamEvent.ToolCallStarted(
                    messageId,
                    node.path("contentIndex").asInt(),
                    requiredText(node, "toolCallId"),
                    requiredText(node, "toolName"));
            case "toolcall_delta" -> new AiStreamEvent.ToolCallDelta(
                    messageId,
                    node.path("contentIndex").asInt(),
                    node.path("delta"));
            case "toolcall_end" -> new AiStreamEvent.ToolCallEnded(
                    messageId,
                    node.path("contentIndex").asInt(),
                    requiredText(node, "toolCallId"));
            case "message_done" -> new AiStreamEvent.MessageCompleted(
                    messageId,
                    new AiAssistantMessage(
                            content(node.path("content")),
                            stopReason(node.path("stopReason").asText("STOP")),
                            usage(node.path("usage")),
                            text(node, "errorMessage").orElse(null)));
            default -> throw new IllegalArgumentException("unsupported recorded provider event type: " + type);
        };
    }

    private static List<AiContentBlock> content(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return AiContentBlocks.parse(node);
    }

    private static AiUsage usage(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return AiUsage.zero();
        }
        return new AiUsage(
                node.path("input").asLong(0),
                node.path("output").asLong(0),
                node.path("cacheRead").asLong(0),
                node.path("reasoning").asLong(0));
    }

    private static AiStopReason stopReason(String value) {
        return AiStopReason.valueOf(value.toUpperCase());
    }

    private static String requiredText(JsonNode node, String field) {
        return text(node, field)
                .orElseThrow(() -> new IllegalArgumentException("missing required text field: " + field));
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? Optional.of(value.asText()) : Optional.empty();
    }
}
