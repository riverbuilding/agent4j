package com.agent4j.core.message;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public sealed interface ContentBlock permits TextBlock, ReasoningBlock, ToolCallBlock, UnknownContentBlock {
    ContentBlockType type();

    JsonNode raw();

    default Optional<String> textValue() {
        return Optional.empty();
    }
}
