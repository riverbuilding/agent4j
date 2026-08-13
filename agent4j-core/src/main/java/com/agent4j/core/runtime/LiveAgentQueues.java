package com.agent4j.core.runtime;

import com.agent4j.core.message.AgentMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Concurrent prompt queues shared by an active session and its agent loop. */
public final class LiveAgentQueues {
    private final ConcurrentLinkedDeque<AgentMessage> steering;
    private final ConcurrentLinkedDeque<AgentMessage> followUps;

    public LiveAgentQueues(List<AgentMessage> steering, List<AgentMessage> followUps) {
        this.steering = new ConcurrentLinkedDeque<>(steering == null ? List.of() : steering);
        this.followUps = new ConcurrentLinkedDeque<>(followUps == null ? List.of() : followUps);
    }

    public void steer(AgentMessage message) {
        steering.addLast(message);
    }

    public void followUp(AgentMessage message) {
        followUps.addLast(message);
    }

    public List<AgentMessage> drain(QueueKind kind, QueueMode mode) {
        ConcurrentLinkedDeque<AgentMessage> queue = kind == QueueKind.STEER ? steering : followUps;
        if (queue.isEmpty()) {
            return List.of();
        }
        if (mode == QueueMode.ONE_AT_A_TIME) {
            AgentMessage message = queue.pollFirst();
            return message == null ? List.of() : List.of(message);
        }
        List<AgentMessage> drained = new ArrayList<>();
        AgentMessage message;
        while ((message = queue.pollFirst()) != null) {
            drained.add(message);
        }
        return List.copyOf(drained);
    }

    public int size(QueueKind kind) {
        return (kind == QueueKind.STEER ? steering : followUps).size();
    }
}
