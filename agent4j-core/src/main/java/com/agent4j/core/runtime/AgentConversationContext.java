package com.agent4j.core.runtime;

import com.agent4j.ai.AiMessage;
import com.agent4j.ai.AiSystemMessage;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record AgentConversationContext(List<AgentMessage> transcriptMessages, List<AgentMessage> generatedMessages) {
    public AgentConversationContext(List<AgentMessage> transcriptMessages, List<AgentMessage> generatedMessages) {
        Objects.requireNonNull(transcriptMessages, "transcriptMessages");
        Objects.requireNonNull(generatedMessages, "generatedMessages");
        this.transcriptMessages = new ArrayList<>(transcriptMessages);
        this.generatedMessages = new ArrayList<>(generatedMessages);
    }

    @Override
    public List<AgentMessage> transcriptMessages() {
        return List.copyOf(transcriptMessages);
    }

    @Override
    public List<AgentMessage> generatedMessages() {
        return List.copyOf(generatedMessages);
    }

    public List<AgentMessage> assistantMessages() {
        return generatedMessages.stream()
                .filter(message -> message.role() == AgentMessageRole.ASSISTANT)
                .toList();
    }

    public void appendGenerated(AgentMessage message) {
        Objects.requireNonNull(message, "message");
        generatedMessages.add(message);
        transcriptMessages.add(message);
    }

    public void recordGenerated(AgentMessage message) {
        generatedMessages.add(Objects.requireNonNull(message, "message"));
    }

    public void replaceTranscript(List<AgentMessage> messages) {
        Objects.requireNonNull(messages, "messages");
        transcriptMessages.clear();
        transcriptMessages.addAll(messages);
    }

    public List<AiMessage> toModelMessages(String systemPrompt, AgentMessageConverter converter) {
        Objects.requireNonNull(converter, "converter");
        List<AiMessage> modelMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            modelMessages.add(new AiSystemMessage(systemPrompt));
        }
        modelMessages.addAll(converter.convertToLlm(transcriptMessages));
        return modelMessages;
    }
}
