package com.agent4j.coding.extension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Runtime-owned, ordered registry of interactive commands contributed by trusted extensions. */
public final class InteractiveCommandRegistry {
    private final Map<String, ExtensionCommandContribution> commands = new LinkedHashMap<>();

    public InteractiveCommandRegistry(List<ExtensionCommandContribution> commands) {
        Objects.requireNonNull(commands, "commands");
        for (ExtensionCommandContribution contribution : commands) {
            ExtensionCommandContribution command = Objects.requireNonNull(contribution, "commands must not contain null");
            String name = command.command().name();
            if (!name.matches("[a-z][a-z0-9-]*")) {
                throw new IllegalArgumentException("invalid interactive command name: " + name);
            }
            if (this.commands.putIfAbsent(name, command) != null) {
                throw new IllegalArgumentException("interactive command is already registered: " + name);
            }
        }
    }

    public List<ExtensionCommandContribution> commands() {
        return List.copyOf(commands.values());
    }

    public Optional<ExtensionCommandContribution> find(String name) {
        return Optional.ofNullable(commands.get(Objects.requireNonNull(name, "name")));
    }
}
