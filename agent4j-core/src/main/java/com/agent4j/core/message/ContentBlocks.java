package com.agent4j.core.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ContentBlocks {
    private ContentBlocks() {
    }

    public static List<ContentBlock> parse(JsonNode content) {
        if (content == null || content.isNull()) {
            return List.of();
        }
        if (content.isTextual()) {
            return List.of(new TextBlock(content.asText(), content));
        }
        if (!content.isArray()) {
            return List.of(new UnknownContentBlock(null, content));
        }
        List<ContentBlock> blocks = new ArrayList<>();
        for (JsonNode block : content) {
            blocks.add(parseBlock(block));
        }
        return List.copyOf(blocks);
    }

    public static ArrayNode toJsonArray(List<? extends ContentBlock> blocks) {
        Objects.requireNonNull(blocks, "blocks");
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (ContentBlock block : blocks) {
            array.add(toJson(block));
        }
        return array;
    }

    public static ObjectNode textJson(String text) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", ContentBlockType.TEXT.wireName());
        node.put("text", text);
        return node;
    }

    public static ObjectNode reasoningJson(String text) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", ContentBlockType.REASONING.wireName());
        node.put("text", text);
        return node;
    }

    public static ObjectNode toolCallJson(ToolCall toolCall) {
        Objects.requireNonNull(toolCall, "toolCall");
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", ContentBlockType.TOOL_CALL.wireName());
        node.put("id", toolCall.id());
        node.put("name", toolCall.name());
        if (toolCall.arguments() == null) {
            node.putNull("arguments");
        } else {
            node.set("arguments", toolCall.arguments());
        }
        return node;
    }

    private static ContentBlock parseBlock(JsonNode block) {
        if (block == null || !block.isObject()) {
            return new UnknownContentBlock(null, block);
        }
        String rawType = textOrNull(block.get("type"));
        return switch (ContentBlockType.fromWireName(rawType)) {
            case TEXT -> new TextBlock(textOrEmpty(block.get("text")), block);
            case REASONING -> new ReasoningBlock(textOrEmpty(block.get("text")), block);
            case TOOL_CALL -> new ToolCallBlock(new ToolCall(
                    textOrEmpty(block.get("id")),
                    textOrEmpty(block.get("name")),
                    block.get("arguments")), block);
            case UNKNOWN -> new UnknownContentBlock(rawType, block);
        };
    }

    private static JsonNode toJson(ContentBlock block) {
        return switch (block) {
            case TextBlock textBlock -> textBlock.raw() == null ? textJson(textBlock.text()) : textBlock.raw();
            case ReasoningBlock reasoningBlock -> reasoningBlock.raw() == null
                    ? reasoningJson(reasoningBlock.text())
                    : reasoningBlock.raw();
            case ToolCallBlock toolCallBlock -> toolCallBlock.raw() == null
                    ? toolCallJson(toolCallBlock.toolCall())
                    : toolCallBlock.raw();
            case UnknownContentBlock unknownContentBlock -> unknownContentBlock.raw();
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
