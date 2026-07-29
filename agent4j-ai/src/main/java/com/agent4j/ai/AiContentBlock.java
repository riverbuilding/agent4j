package com.agent4j.ai;

public sealed interface AiContentBlock permits AiTextContent, AiThinkingContent, AiImageContent, AiToolCallContent {
    String type();
}
