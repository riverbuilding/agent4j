package com.agent4j.coding.message;

import com.agent4j.core.message.AgentMessage;

import java.util.Objects;

public record BashExecutionMessage(String command, Integer exitCode, String output) {
    public BashExecutionMessage {
        command = command == null ? "" : command;
        output = output == null ? "" : output;
    }

    public static BashExecutionMessage from(AgentMessage message) {
        Objects.requireNonNull(message, "message");
        return new BashExecutionMessage(
                CodingAgentMessages.textField(message, "command"),
                CodingAgentMessages.intField(message, "exitCode"),
                CodingAgentMessages.textContent(message));
    }

    public String toLlmText() {
        StringBuilder text = new StringBuilder();
        text.append("<bashExecution>\n");
        if (!command.isBlank()) {
            text.append("Command: ").append(command).append('\n');
        }
        if (exitCode != null) {
            text.append("Exit code: ").append(exitCode).append('\n');
        }
        if (!output.isBlank()) {
            text.append("Output:\n").append(output).append('\n');
        }
        text.append("</bashExecution>");
        return text.toString();
    }
}
