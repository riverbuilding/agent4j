package com.agent4j.core.tool;

import java.util.Objects;

public record RegisteredTool(ToolSpec spec, Tool tool) {
    public RegisteredTool {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(tool, "tool");
    }
}
