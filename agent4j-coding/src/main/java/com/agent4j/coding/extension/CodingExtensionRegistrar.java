package com.agent4j.coding.extension;

import com.agent4j.core.tool.Tool;
import com.agent4j.core.tool.ToolSpec;

/** Registration boundary made available while a {@link CodingAgentExtension} is initialized. */
public interface CodingExtensionRegistrar {
    void registerTool(ToolSpec specification, Tool tool);

    void registerCommand(CodingExtensionCommand command);
}
