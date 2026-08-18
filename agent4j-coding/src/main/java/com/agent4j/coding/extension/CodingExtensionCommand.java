package com.agent4j.coding.extension;

import java.util.Objects;
import java.util.Optional;

/** A command contributed by a coding extension, independent of a particular terminal UI. */
public record CodingExtensionCommand(String name, String description, CodingExtensionCommandHandler handler) {
    public CodingExtensionCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        if (name.isBlank()) {
            throw new IllegalArgumentException("command name must not be blank");
        }
    }

    public Optional<String> optionalDescription() {
        return Optional.ofNullable(description);
    }
}
