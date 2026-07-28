package com.agent4j.core.tool;

import java.util.Collection;
import java.util.Optional;

public interface ToolRegistry {
    Optional<RegisteredTool> find(String name);

    Collection<ToolSpec> specs();
}
