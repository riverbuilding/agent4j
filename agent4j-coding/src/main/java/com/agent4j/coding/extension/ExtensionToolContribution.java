package com.agent4j.coding.extension;

import com.agent4j.core.tool.Tool;
import com.agent4j.core.tool.ToolSpec;

import java.util.Objects;

/** A tool contributed by one extension. */
public record ExtensionToolContribution(String extensionName, ToolSpec specification, Tool tool) {
    public ExtensionToolContribution {
        Objects.requireNonNull(extensionName, "extensionName");
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(tool, "tool");
    }
}
