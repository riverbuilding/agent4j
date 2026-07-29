package com.agent4j.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AiContentBlocks {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private AiContentBlocks() {
    }

    public static List<AiContentBlock> parse(JsonNode content) {
        if (content == null || content.isNull()) {
            return List.of();
        }
        if (content.isTextual()) {
            return List.of(new AiTextContent(content.asText()));
        }
        if (!content.isArray()) {
            return List.of(new AiTextContent(content.toString()));
        }
        List<AiContentBlock> blocks = new ArrayList<>();
        for (JsonNode block : content) {
            blocks.add(parseBlock(block));
        }
        return List.copyOf(blocks);
    }

    public static ArrayNode toJsonArray(List<? extends AiContentBlock> blocks) {
        Objects.requireNonNull(blocks, "blocks");
        ArrayNode array = JSON.arrayNode();
        for (AiContentBlock block : blocks) {
            array.add(toJson(block));
        }
        return array;
    }

    public static ObjectNode toJson(AiContentBlock block) {
        return switch (block) {
            case AiTextContent text -> JSON.objectNode()
                    .put("type", "text")
                    .put("text", text.text());
            case AiThinkingContent thinking -> {
                ObjectNode node = JSON.objectNode()
                        .put("type", "thinking")
                        .put("thinking", thinking.thinking());
                if (thinking.thinkingSignature() != null) {
                    node.put("thinkingSignature", thinking.thinkingSignature());
                }
                node.put("redacted", thinking.redacted());
                yield node;
            }
            case AiImageContent image -> JSON.objectNode()
                    .put("type", "image")
                    .put("data", image.data())
                    .put("mimeType", image.mimeType());
            case AiToolCallContent toolCall -> {
                ObjectNode node = JSON.objectNode()
                        .put("type", "toolCall")
                        .put("id", toolCall.id())
                        .put("name", toolCall.name());
                node.set("arguments", toolCall.arguments());
                if (toolCall.thoughtSignature() != null) {
                    node.put("thoughtSignature", toolCall.thoughtSignature());
                }
                yield node;
            }
        };
    }

    private static AiContentBlock parseBlock(JsonNode block) {
        if (block == null || !block.isObject()) {
            return new AiTextContent(block == null ? "" : block.toString());
        }
        String type = textOrNull(block.get("type"));
        return switch (type == null ? "" : type) {
            case "text" -> new AiTextContent(textOrEmpty(block.get("text")));
            case "thinking", "reasoning" -> new AiThinkingContent(
                    textOrEmpty(block.get("thinking") == null ? block.get("text") : block.get("thinking")),
                    textOrNull(block.get("thinkingSignature")),
                    block.path("redacted").asBoolean(false));
            case "image" -> new AiImageContent(textOrEmpty(block.get("data")), textOrEmpty(block.get("mimeType")));
            case "toolCall", "tool_call" -> new AiToolCallContent(
                    textOrEmpty(block.get("id")),
                    textOrEmpty(block.get("name")),
                    block.get("arguments") == null ? JSON.objectNode() : block.get("arguments"),
                    textOrNull(block.get("thoughtSignature")));
            default -> new AiTextContent(block.toString());
        };
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static String textOrEmpty(JsonNode node) {
        String value = textOrNull(node);
        return value == null ? "" : value;
    }
}
