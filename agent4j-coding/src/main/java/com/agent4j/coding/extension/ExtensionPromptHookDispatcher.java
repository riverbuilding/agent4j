package com.agent4j.coding.extension;

import com.agent4j.core.message.AgentMessage;

import java.util.List;
import java.util.Objects;

/** Runs prompt hooks in registration order while isolating an individual hook failure. */
public final class ExtensionPromptHookDispatcher {
    private static final System.Logger LOGGER = System.getLogger(ExtensionPromptHookDispatcher.class.getName());

    private ExtensionPromptHookDispatcher() {
    }

    public static CodingExtensionAgentStart beforeAgentStart(
            List<ExtensionAgentStartHookContribution> contributions,
            CodingExtensionAgentStart event,
            ExtensionContext context
    ) {
        CodingExtensionAgentStart current = Objects.requireNonNull(event, "event");
        Objects.requireNonNull(contributions, "contributions");
        Objects.requireNonNull(context, "context");
        for (ExtensionAgentStartHookContribution contribution : contributions) {
            try {
                current = Objects.requireNonNull(contribution.hook().beforeAgentStart(current, context), "hook result");
            } catch (Exception error) {
                report("agent-start", contribution.extensionName(), contribution.name(), error);
            }
        }
        return current;
    }

    public static List<AgentMessage> transformContext(
            List<ExtensionContextTransformHookContribution> contributions,
            List<AgentMessage> messages,
            ExtensionContext context
    ) {
        List<AgentMessage> current = List.copyOf(Objects.requireNonNull(messages, "messages"));
        Objects.requireNonNull(contributions, "contributions");
        Objects.requireNonNull(context, "context");
        for (ExtensionContextTransformHookContribution contribution : contributions) {
            try {
                current = List.copyOf(Objects.requireNonNull(contribution.hook().transformContext(current, context), "hook result"));
            } catch (Exception error) {
                report("context-transform", contribution.extensionName(), contribution.name(), error);
            }
        }
        return current;
    }

    private static void report(String type, String extensionName, String name, Exception error) {
        LOGGER.log(System.Logger.Level.WARNING,
                "extension {0} {1} hook {2} failed: {3}", extensionName, type, name, error.toString());
    }
}
