package com.agent4j.coding.extension;

/** Transforms prompt values before an agent turn begins. */
@FunctionalInterface
public interface ExtensionAgentStartHook {
    CodingExtensionAgentStart beforeAgentStart(
            CodingExtensionAgentStart event,
            ExtensionContext context
    ) throws Exception;
}
