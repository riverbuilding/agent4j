package com.agent4j.cli;

import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.message.AgentMessageRole;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Structured event-to-terminal rendering shared by styled and line fallbacks. */
interface TerminalEventRenderer {
    void render(AgentEvent event);

    final class Factory {
        private Factory() { }

        static TerminalEventRenderer create(InteractiveTerminal terminal) {
            return terminal.ansiEnabled()
                    ? new JLineTerminalEventRenderer(terminal)
                    : new LineTerminalEventRenderer(terminal);
        }
    }

    abstract class Base implements TerminalEventRenderer {
        protected final InteractiveTerminal terminal;
        private final Map<String, Boolean> streamedText = new HashMap<>();
        private boolean lineOpen;

        protected Base(InteractiveTerminal terminal) {
            this.terminal = Objects.requireNonNull(terminal, "terminal");
        }

        @Override
        public synchronized void render(AgentEvent event) {
            switch (event) {
                case AgentEvent.MessageUpdated updated -> renderMessageUpdate(updated);
                case AgentEvent.MessageEnded ended -> renderMessageEnd(ended);
                case AgentEvent.ToolExecutionStarted started -> status("tool " + started.toolCall().name() + " started");
                case AgentEvent.ToolExecutionUpdated updated -> status("tool " + updated.toolCallId() + " update: " + compact(updated.delta()));
                case AgentEvent.ToolExecutionEnded ended -> status("tool " + ended.result().toolName()
                        + (ended.result().error() ? " failed: " : " completed: ") + compact(ended.result().content()));
                case AgentEvent.RetryStarted started -> status("retry " + started.attempt() + ": " + started.reason());
                case AgentEvent.RetryCompleted completed -> status("retry " + completed.attempt()
                        + (completed.success() ? " completed" : " failed"));
                case AgentEvent.CompactionStarted started -> status("compacting: " + started.reason());
                case AgentEvent.CompactionCompleted completed -> status("compaction completed: " + completed.summaryMessageId());
                case AgentEvent.QueueUpdated updated -> status(updated.queueKind().wireName() + " queue: " + updated.size());
                case AgentEvent.AgentAborted aborted -> error("aborted: " + aborted.reason());
                default -> { }
            }
        }

        private void renderMessageUpdate(AgentEvent.MessageUpdated updated) {
            JsonNode delta = updated.delta();
            String type = delta.path("type").asText();
            if ("text_delta".equals(type)) {
                terminal.out().print(delta.path("delta").asText());
                terminal.out().flush();
                streamedText.put(updated.messageId(), true);
                lineOpen = true;
            } else if ("message_error".equals(type)) {
                error(delta.path("error").asText("model stream failed"));
            }
        }

        private void renderMessageEnd(AgentEvent.MessageEnded ended) {
            if (ended.message().role() != AgentMessageRole.ASSISTANT) return;
            boolean streamed = streamedText.remove(ended.message().id()) != null;
            String finalText = ended.message().textContent();
            if (!streamed && !finalText.isEmpty()) {
                finishLine();
                markdown(finalText);
            } else if (streamed && lineOpen) {
                terminal.out().println();
                terminal.out().flush();
                lineOpen = false;
            }
        }

        protected abstract void markdown(String value);

        protected abstract void status(String value);

        protected abstract void error(String value);

        protected void finishLine() {
            if (lineOpen) {
                terminal.out().println();
                terminal.out().flush();
                lineOpen = false;
            }
        }

        private static String compact(JsonNode value) {
            if (value == null || value.isNull()) return "";
            String text = value.isTextual() ? value.asText() : value.toString();
            return text.length() <= 240 ? text : text.substring(0, 237) + "...";
        }
    }
}
