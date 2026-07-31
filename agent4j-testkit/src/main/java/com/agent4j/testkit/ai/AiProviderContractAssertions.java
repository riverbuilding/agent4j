package com.agent4j.testkit.ai;

import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStopReason;
import com.agent4j.ai.AiStreamEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class AiProviderContractAssertions {
    private AiProviderContractAssertions() {
    }

    public static void assertNormalizedStreamContract(AiProvider provider, AiProviderRequest request) throws Exception {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(request, "request");
        provider.stream(request, assertNormalizedStreamContract());
    }

    public static Consumer<AiStreamEvent> assertNormalizedStreamContract() {
        return new Consumer<>() {
            private boolean started;
            private boolean terminal;
            private String messageId;

            @Override
            public void accept(AiStreamEvent event) {
                if (terminal) {
                    throw new AssertionError("provider emitted event after terminal event: " + event);
                }
                switch (event) {
                    case AiStreamEvent.MessageStarted startedEvent -> {
                        if (started) {
                            throw new AssertionError("provider emitted more than one message_start event");
                        }
                        started = true;
                        messageId = startedEvent.messageId();
                    }
                    case AiStreamEvent.MessageErrored errored -> {
                        assertStarted(errored.messageId());
                        terminal = true;
                    }
                    case AiStreamEvent.MessageCompleted completed -> {
                        assertStarted(completed.messageId());
                        if (completed.message().stopReason() == AiStopReason.ERROR
                                || completed.message().stopReason() == AiStopReason.ABORTED) {
                            if (completed.message().errorMessage() == null || completed.message().errorMessage().isBlank()) {
                                throw new AssertionError("error/aborted message must include errorMessage");
                            }
                        }
                        terminal = true;
                    }
                    case AiStreamEvent.TextStarted text -> assertStarted(text.messageId());
                    case AiStreamEvent.TextDelta text -> assertStarted(text.messageId());
                    case AiStreamEvent.TextEnded text -> assertStarted(text.messageId());
                    case AiStreamEvent.ThinkingStarted thinking -> assertStarted(thinking.messageId());
                    case AiStreamEvent.ThinkingDelta thinking -> assertStarted(thinking.messageId());
                    case AiStreamEvent.ThinkingEnded thinking -> assertStarted(thinking.messageId());
                    case AiStreamEvent.ToolCallStarted toolCall -> assertStarted(toolCall.messageId());
                    case AiStreamEvent.ToolCallDelta toolCall -> assertStarted(toolCall.messageId());
                    case AiStreamEvent.ToolCallEnded toolCall -> assertStarted(toolCall.messageId());
                }
            }

            private void assertStarted(String eventMessageId) {
                if (!started) {
                    throw new AssertionError("provider emitted content before message_start");
                }
                if (!Objects.equals(messageId, eventMessageId)) {
                    throw new AssertionError("event message id " + eventMessageId + " does not match started id " + messageId);
                }
            }
        };
    }

    public static void assertNoEventsAfterTerminal(List<AiStreamEvent> events) {
        boolean terminal = false;
        for (AiStreamEvent event : events) {
            if (terminal) {
                throw new AssertionError("provider emitted event after terminal event: " + event);
            }
            terminal = event instanceof AiStreamEvent.MessageCompleted
                    || event instanceof AiStreamEvent.MessageErrored;
        }
    }
}
