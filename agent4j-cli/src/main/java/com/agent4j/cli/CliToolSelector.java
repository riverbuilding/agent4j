package com.agent4j.cli;

import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.RegisteredTool;
import com.agent4j.core.tool.ToolRegistry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class CliToolSelector {
    private CliToolSelector() {
    }

    static ToolRegistry select(ToolRegistry source, CliToolSelection selection) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(selection, "selection");
        if ((selection.noTools() || selection.noBuiltinTools()) && selection.included().isPresent()) {
            throw new IllegalArgumentException("--tools cannot be combined with --no-tools or --no-builtin-tools");
        }
        Set<String> available = source.specs().stream()
                .map(spec -> spec.name())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> included = new LinkedHashSet<>(selection.included().orElse(List.copyOf(available)));
        LinkedHashSet<String> excluded = new LinkedHashSet<>(selection.excluded());
        validateNames(included, available);
        validateNames(excluded, available);
        if (selection.noTools() || selection.noBuiltinTools()) {
            return InMemoryToolRegistry.builder().build();
        }
        included.removeAll(excluded);
        InMemoryToolRegistry.Builder selected = InMemoryToolRegistry.builder();
        for (String name : available) {
            if (included.contains(name)) {
                RegisteredTool tool = source.find(name).orElseThrow();
                selected.register(tool.spec(), tool.tool());
            }
        }
        return selected.build();
    }

    private static void validateNames(Set<String> names, Set<String> available) {
        for (String name : names) {
            if (name == null || name.isBlank() || !available.contains(name)) {
                throw new IllegalArgumentException("unknown tool: " + name);
            }
        }
    }
}
