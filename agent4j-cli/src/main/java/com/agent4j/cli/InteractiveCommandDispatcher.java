package com.agent4j.cli;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Dispatches built-in and runtime-provided interactive commands. */
final class InteractiveCommandDispatcher {
    private final Map<String, InteractiveCommand> commands = new LinkedHashMap<>();

    void register(String name, InteractiveCommand command) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(command, "command");
        if (!name.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("invalid command name: " + name);
        }
        if (commands.putIfAbsent(name, command) != null) {
            throw new IllegalArgumentException("command is already registered: " + name);
        }
    }

    InteractiveCommandResult execute(String input) throws Exception {
        if (input == null || !input.startsWith("/")) {
            return InteractiveCommandResult.notACommand();
        }
        String body = input.substring(1).strip();
        int separator = body.indexOf(' ');
        String name = (separator < 0 ? body : body.substring(0, separator)).toLowerCase(java.util.Locale.ROOT);
        String arguments = separator < 0 ? "" : body.substring(separator + 1).strip();
        InteractiveCommand command = commands.get(name);
        if (command == null) {
            throw new IllegalArgumentException("unknown command: /" + name);
        }
        return command.execute(arguments);
    }
}
