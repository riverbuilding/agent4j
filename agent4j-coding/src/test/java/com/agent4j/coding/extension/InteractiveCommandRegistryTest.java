package com.agent4j.coding.extension;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InteractiveCommandRegistryTest {
    @Test
    void preservesExtensionRegistrationOrder() {
        InteractiveCommandRegistry registry = new InteractiveCommandRegistry(List.of(
                contribution("first"), contribution("second")));

        assertThat(registry.commands()).extracting(contribution -> contribution.command().name())
                .containsExactly("first", "second");
        assertThat(registry.find("second")).isPresent();
    }

    @Test
    void rejectsSlashCommandNames() {
        assertThatThrownBy(() -> new InteractiveCommandRegistry(List.of(contribution("/status"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid interactive command name: /status");
    }

    private static ExtensionCommandContribution contribution(String name) {
        return new ExtensionCommandContribution("extension",
                new CodingExtensionCommand(name, null, (arguments, context) -> {
                }));
    }
}
