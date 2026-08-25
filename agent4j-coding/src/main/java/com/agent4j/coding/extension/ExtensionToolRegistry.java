package com.agent4j.coding.extension;

import com.agent4j.core.tool.RegisteredTool;
import com.agent4j.core.tool.ToolRegistry;
import com.agent4j.core.tool.ToolSpec;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Merges extension tools with an existing registry without allowing replacement of built-in tools. */
public final class ExtensionToolRegistry implements ToolRegistry {
    private final Map<String, RegisteredTool> tools;

    private ExtensionToolRegistry(Map<String, RegisteredTool> tools) {
        this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(tools));
    }

    public static ToolRegistry merge(ToolRegistry baseRegistry, ResolvedExtensionContributions contributions) {
        Objects.requireNonNull(baseRegistry, "baseRegistry");
        Objects.requireNonNull(contributions, "contributions");
        Map<String, RegisteredTool> tools = new LinkedHashMap<>();
        for (ToolSpec specification : baseRegistry.specs()) {
            RegisteredTool tool = baseRegistry.find(specification.name())
                    .orElseThrow(() -> new IllegalStateException("tool registry is missing " + specification.name()));
            tools.put(specification.name(), tool);
        }
        for (ExtensionToolContribution contribution : contributions.tools()) {
            String name = contribution.specification().name();
            RegisteredTool previous = tools.putIfAbsent(name, new RegisteredTool(
                    contribution.specification(), contribution.tool()));
            if (previous != null) {
                throw new IllegalArgumentException("duplicate tool name: " + name);
            }
        }
        return new ExtensionToolRegistry(tools);
    }

    @Override
    public Optional<RegisteredTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    @Override
    public Collection<ToolSpec> specs() {
        return tools.values().stream().map(RegisteredTool::spec).toList();
    }
}
