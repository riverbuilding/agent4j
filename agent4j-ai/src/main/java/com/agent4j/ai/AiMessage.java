package com.agent4j.ai;

public sealed interface AiMessage permits AiUserMessage, AiAssistantMessage, AiToolResultMessage {
    String role();
}
