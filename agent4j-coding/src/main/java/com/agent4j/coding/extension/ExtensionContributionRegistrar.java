package com.agent4j.coding.extension;

import com.agent4j.core.tool.Tool;
import com.agent4j.core.tool.ToolExecutionHook;
import com.agent4j.core.tool.ToolSpec;

/** Registration boundary available to an {@link AgentExtension}. */
public interface ExtensionContributionRegistrar {
    void registerTool(ToolSpec specification, Tool tool);

    void registerHook(String name, ToolExecutionHook hook);

    void registerAgentStartHook(String name, ExtensionAgentStartHook hook);

    void registerContextTransformHook(String name, ExtensionContextTransformHook hook);

    void registerCommand(CodingExtensionCommand command);

    void registerLifecycleListener(String name, ExtensionLifecycleListener listener);
}
