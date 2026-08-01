package com.agent4j.core.compaction;

import com.agent4j.core.message.AgentMessage;

import java.util.List;
import java.util.Objects;

public interface TokenEstimator {
    long estimateText(String text);

    default long estimateMessage(AgentMessage message) {
        Objects.requireNonNull(message, "message");
        return estimateText(message.role().wireName()) + estimateText(message.textContent());
    }

    default long estimateMessages(List<AgentMessage> messages) {
        Objects.requireNonNull(messages, "messages");
        long total = 0;
        for (AgentMessage message : messages) {
            total += estimateMessage(message);
        }
        return total;
    }
}
