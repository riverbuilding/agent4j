package com.agent4j.cli;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InteractiveCommandRegistryTest {
    @Test
    void parsesCaseInsensitiveCommandNamesAndTrimmedArguments() throws Exception {
        InteractiveCommandRegistry registry = new InteractiveCommandRegistry();
        AtomicReference<String> received = new AtomicReference<>();
        registry.register("name", arguments -> {
            received.set(arguments);
            return InteractiveCommandResult.handledResult();
        });

        InteractiveCommandResult result = registry.execute("/NAME   feature work  ");

        assertThat(result.handled()).isTrue();
        assertThat(result.exit()).isFalse();
        assertThat(received.get()).isEqualTo("feature work");
        assertThat(registry.execute("ordinary prompt").handled()).isFalse();
    }

    @Test
    void rejectsDuplicateAndUnknownCommands() {
        InteractiveCommandRegistry registry = new InteractiveCommandRegistry();
        registry.register("status", ignored -> InteractiveCommandResult.handledResult());

        assertThatThrownBy(() -> registry.register("status", ignored -> InteractiveCommandResult.handledResult()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
        assertThatThrownBy(() -> registry.execute("/missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown command");
    }
}
