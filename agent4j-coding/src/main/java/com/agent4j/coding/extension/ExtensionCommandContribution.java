package com.agent4j.coding.extension;

import java.util.Objects;

/** A command contributed by one extension. */
public record ExtensionCommandContribution(String extensionName, CodingExtensionCommand command) {
    public ExtensionCommandContribution {
        Objects.requireNonNull(extensionName, "extensionName");
        Objects.requireNonNull(command, "command");
    }
}
