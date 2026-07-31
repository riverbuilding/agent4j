package com.agent4j.ai;

public sealed interface AiMessage permits AiSystemMessage, AiUserMessage, AiAssistantMessage, AiToolResultMessage {
    String role();
}
