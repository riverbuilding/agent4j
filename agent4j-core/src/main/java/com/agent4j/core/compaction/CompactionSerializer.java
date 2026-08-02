package com.agent4j.core.compaction;

import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlock;
import com.agent4j.core.message.TextBlock;
import com.agent4j.core.message.ToolCallBlock;
import com.agent4j.core.message.ToolResultAgentMessageView;
import com.agent4j.core.message.UnknownContentBlock;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class CompactionSerializer {
    public static final int DEFAULT_MAX_TOOL_RESULT_CHARS = 500;

    private final int maxToolResultChars;

    public CompactionSerializer() {
        this(DEFAULT_MAX_TOOL_RESULT_CHARS);
    }

    public CompactionSerializer(int maxToolResultChars) {
        if (maxToolResultChars < 0) {
            throw new IllegalArgumentException("maxToolResultChars must be non-negative");
        }
        this.maxToolResultChars = maxToolResultChars;
    }

    public String serialize(List<AgentMessage> messages) {
        Objects.requireNonNull(messages, "messages");
        return messages.stream()
                .filter(message -> message.role() != AgentMessageRole.SYSTEM)
                .map(this::renderMessage)
                .filter(rendered -> !rendered.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    public String buildSummaryPrompt(CompactionRequest request, List<AgentMessage> prefixMessages) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(prefixMessages, "prefixMessages");
        String serializedMessages = serialize(prefixMessages);
        String prompt = request.config().summaryPrompt().replace("{messages}", serializedMessages);
        return request.optionalFocusInstructions()
                .map(focus -> prompt + "\n\n<focusInstructions>\n" + focus + "\n</focusInstructions>")
                .orElse(prompt);
    }

    private String renderMessage(AgentMessage message) {
        String body = switch (message.role()) {
            case TOOL_RESULT -> renderToolResult(message);
            default -> renderBlocks(message);
        };
        if (body.isBlank()) {
            return "";
        }
        return roleLabel(message.role()) + ": " + body.strip();
    }

    private String renderBlocks(AgentMessage message) {
        return message.contentBlocks().stream()
                .map(block -> renderBlock(message.role(), block))
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String renderBlock(AgentMessageRole role, ContentBlock block) {
        return switch (block) {
            case TextBlock textBlock -> textBlock.text().strip();
            case ToolCallBlock toolCallBlock -> "[tool_call: "
                    + toolCallBlock.toolCall().name()
                    + " id="
                    + toolCallBlock.toolCall().id()
                    + renderArguments(toolCallBlock.toolCall().arguments())
                    + "]";
            case UnknownContentBlock unknownContentBlock -> renderUnknown(role, unknownContentBlock);
            default -> block.textValue().orElse("").strip();
        };
    }

    private String renderToolResult(AgentMessage message) {
        ToolResultAgentMessageView view = (ToolResultAgentMessageView) message.view();
        String text = message.textContent();
        if (text.isBlank()) {
            text = renderJson(message.content());
        }
        StringBuilder builder = new StringBuilder("[tool_result: ")
                .append(view.toolName().isBlank() ? "?" : view.toolName());
        if (!view.toolCallId().isBlank()) {
            builder.append(" id=").append(view.toolCallId());
        }
        if (view.error()) {
            builder.append(" error=true");
        }
        builder.append("]");
        String clipped = clip(text, maxToolResultChars);
        if (!clipped.isBlank()) {
            builder.append(" ").append(clipped);
        }
        return builder.toString();
    }

    private static String renderArguments(JsonNode arguments) {
        if (arguments == null || arguments.isNull()) {
            return "";
        }
        return " args=" + arguments.toString();
    }

    private static String renderUnknown(AgentMessageRole role, UnknownContentBlock block) {
        if (role == AgentMessageRole.COMPACTION_SUMMARY || role == AgentMessageRole.BRANCH_SUMMARY) {
            return renderJson(block.raw());
        }
        return block.textValue().orElse("");
    }

    private static String renderJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    private static String clip(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String stripped = text.strip();
        if (maxChars == 0 || stripped.length() <= maxChars) {
            return stripped;
        }
        return stripped.substring(0, maxChars) + "...";
    }

    private static String roleLabel(AgentMessageRole role) {
        return switch (role) {
            case USER -> "Human";
            case ASSISTANT -> "AI";
            case TOOL_RESULT -> "Tool";
            case COMPACTION_SUMMARY -> "Compaction Summary";
            case BRANCH_SUMMARY -> "Branch Summary";
            case BASH_EXECUTION -> "Bash";
            case CUSTOM -> "Custom";
            case SYSTEM -> "System";
            case UNKNOWN -> "Unknown";
        };
    }
}
