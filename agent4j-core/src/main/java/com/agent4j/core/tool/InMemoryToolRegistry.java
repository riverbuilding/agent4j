package com.agent4j.core.tool;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryToolRegistry implements ToolRegistry {
    private final Map<String, RegisteredTool> tools;

    private InMemoryToolRegistry(Map<String, RegisteredTool> tools) {
        this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(tools));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<RegisteredTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    @Override
    public Collection<ToolSpec> specs() {
        return tools.values().stream().map(RegisteredTool::spec).toList();
    }

    public static final class Builder {
        private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

        public Builder register(ToolSpec spec, Tool tool) {
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(tool, "tool");
            RegisteredTool previous = tools.putIfAbsent(spec.name(), new RegisteredTool(spec, tool));
            if (previous != null) {
                throw new IllegalArgumentException("duplicate tool name: " + spec.name());
            }
            return this;
        }

        public InMemoryToolRegistry build() {
            return new InMemoryToolRegistry(tools);
        }
    }
}
