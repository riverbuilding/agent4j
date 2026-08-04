package com.agent4j.core.compaction;

import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlock;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolCallBlock;
import com.agent4j.core.message.ToolResultAgentMessageView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CompactionMessagePreprocessor {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final TokenEstimator tokenEstimator;

    CompactionMessagePreprocessor(TokenEstimator tokenEstimator) {
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator, "tokenEstimator");
    }

    List<AgentMessage> prepare(List<AgentMessage> messages, CompactionConfig config) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(config, "config");
        return pruneToolResults(truncateArgs(messages, config.truncateArgsConfig()), config.pruneConfig());
    }

    List<AgentMessage> pruneToolResults(List<AgentMessage> messages, CompactionConfig.PruneConfig pruneConfig) {
        if (pruneConfig == null || messages.isEmpty()) {
            return messages;
        }

        long protectedTokens = 0;
        long prunableTokens = 0;
        List<Integer> toPrune = new ArrayList<>();
        Set<String> excluded = pruneConfig.excludedTools();

        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (message.role() != AgentMessageRole.TOOL_RESULT) {
                continue;
            }
            ToolResultAgentMessageView view = (ToolResultAgentMessageView) message.view();
            if (excluded.contains(view.toolName())) {
                continue;
            }
            String text = toolResultText(message);
            if (text.isBlank()) {
                continue;
            }
            long tokens = tokenEstimator.estimateMessage(message);
            if (protectedTokens < pruneConfig.protectTokens()) {
                protectedTokens += tokens;
                continue;
            }
            if (text.length() > pruneConfig.maxOutputChars()) {
                prunableTokens += tokens;
                toPrune.add(i);
            }
        }

        if (toPrune.isEmpty() || prunableTokens < pruneConfig.minimumTokens()) {
            return messages;
        }

        List<AgentMessage> result = new ArrayList<>(messages);
        for (Integer index : toPrune) {
            result.set(index, prunedToolResult(result.get(index), pruneConfig.maxOutputChars()));
        }
        return List.copyOf(result);
    }

    List<AgentMessage> truncateArgs(
            List<AgentMessage> messages,
            CompactionConfig.TruncateArgsConfig truncateConfig
    ) {
        if (truncateConfig == null || messages.isEmpty()) {
            return messages;
        }
        long totalTokens = tokenEstimator.estimateMessages(messages);
        if (!shouldTruncateArgs(messages, totalTokens, truncateConfig)) {
            return messages;
        }

        int cutoff = determineTruncateCutoff(messages, truncateConfig);
        if (cutoff >= messages.size()) {
            return messages;
        }

        boolean changed = false;
        List<AgentMessage> result = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            AgentMessage message = messages.get(i);
            AgentMessage transformed = i < cutoff && message.role() == AgentMessageRole.ASSISTANT
                    ? truncateToolUseArgs(message, truncateConfig)
                    : message;
            result.add(transformed);
            if (transformed != message) {
                changed = true;
            }
        }
        return changed ? List.copyOf(result) : messages;
    }

    private static boolean shouldTruncateArgs(
            List<AgentMessage> messages,
            long totalTokens,
            CompactionConfig.TruncateArgsConfig config
    ) {
        if (config.triggerMessages() > 0 && messages.size() >= config.triggerMessages()) {
            return true;
        }
        return config.triggerTokens() > 0 && totalTokens >= config.triggerTokens();
    }

    private int determineTruncateCutoff(
            List<AgentMessage> messages,
            CompactionConfig.TruncateArgsConfig config
    ) {
        if (config.keepTokens() > 0) {
            long tokensKept = 0;
            for (int i = messages.size() - 1; i >= 0; i--) {
                long messageTokens = tokenEstimator.estimateMessage(messages.get(i));
                if (tokensKept + messageTokens > config.keepTokens()) {
                    return i + 1;
                }
                tokensKept += messageTokens;
            }
            return 0;
        }
        return Math.max(0, messages.size() - config.keepMessages());
    }

    private static AgentMessage truncateToolUseArgs(
            AgentMessage message,
            CompactionConfig.TruncateArgsConfig config
    ) {
        List<ContentBlock> blocks = message.contentBlocks();
        if (blocks.isEmpty()) {
            return message;
        }

        boolean changed = false;
        ArrayNode content = JSON.arrayNode();
        for (ContentBlock block : blocks) {
            if (block instanceof ToolCallBlock toolCallBlock) {
                ToolCall original = toolCallBlock.toolCall();
                JsonNode truncatedArgs = truncateArguments(original.arguments(), config);
                if (truncatedArgs != original.arguments()) {
                    changed = true;
                }
                content.add(ContentBlocks.toolCallJson(new ToolCall(
                        original.id(),
                        original.name(),
                        truncatedArgs)));
            } else {
                content.add(ContentBlocks.toJsonArray(List.of(block)).get(0));
            }
        }
        if (!changed) {
            return message;
        }
        return new AgentMessage(
                message.id(),
                message.parentId(),
                message.timestamp(),
                message.role(),
                content,
                message.metadata());
    }

    private static JsonNode truncateArguments(JsonNode arguments, CompactionConfig.TruncateArgsConfig config) {
        if (arguments == null || arguments.isNull() || !arguments.isObject()) {
            return arguments;
        }
        ObjectNode object = (ObjectNode) arguments;
        ObjectNode copy = null;
        java.util.Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (value != null && value.isTextual() && value.asText().length() > config.maxArgLength()) {
                if (copy == null) {
                    copy = object.deepCopy();
                }
                copy.put(field.getKey(), value.asText().substring(0, 20) + config.truncationText());
            }
        }
        return copy == null ? arguments : copy;
    }

    private static AgentMessage prunedToolResult(AgentMessage message, int maxOutputChars) {
        String text = toolResultText(message);
        String preview = buildPrunePreview(text, maxOutputChars);
        return new AgentMessage(
                message.id(),
                message.parentId(),
                message.timestamp(),
                message.role(),
                ContentBlocks.toJsonArray(List.of(new TextBlock(preview, null))),
                message.metadata());
    }

    private static String toolResultText(AgentMessage message) {
        String text = message.textContent();
        if (!text.isBlank()) {
            return text;
        }
        JsonNode content = message.content();
        if (content == null || content.isNull()) {
            return "";
        }
        return content.isTextual() ? content.asText() : content.toString();
    }

    private static String buildPrunePreview(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (maxChars == 0) {
            return "...(" + text.length() + " chars pruned)...";
        }
        int half = maxChars / 2;
        String head = text.substring(0, Math.min(half, text.length()));
        String tail = text.length() > half
                ? text.substring(Math.max(text.length() - half, half))
                : "";
        int removed = text.length() - head.length() - tail.length();
        if (removed <= 0) {
            return text;
        }
        return head + "\n\n...(" + removed + " chars pruned)...\n\n" + tail;
    }
}
