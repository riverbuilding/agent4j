package com.agent4j.cli;

import com.agent4j.core.message.ToolResult;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.ToolRegistry;
import com.agent4j.core.tool.ToolSpec;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliToolSelectorTest {
    @Test
    void includesAndExcludesNamedToolsInSourceOrder() {
        ToolRegistry selected = CliToolSelector.select(registry(), new CliToolSelection(
                Optional.of(List.of("grep", "read")), List.of("grep"), false, false));

        assertThat(selected.specs()).extracting(ToolSpec::name).containsExactly("read");
    }

    @Test
    void disablesAllBuiltInToolsAndRejectsUnknownOrConflictingInput() {
        assertThat(CliToolSelector.select(registry(), new CliToolSelection(Optional.empty(), List.of(), false, true)).specs()).isEmpty();
        assertThatThrownBy(() -> CliToolSelector.select(registry(), new CliToolSelection(
                Optional.of(List.of("missing")), List.of(), false, false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("unknown tool: missing");
        assertThatThrownBy(() -> CliToolSelector.select(registry(), new CliToolSelection(
                Optional.of(List.of("read")), List.of(), true, false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("--tools cannot be combined");
    }

    private static ToolRegistry registry() {
        return InMemoryToolRegistry.builder()
                .register(new ToolSpec("read", "Read", JsonNodeFactory.instance.objectNode()), (call, context) -> ToolResult.blocked(call, "unused"))
                .register(new ToolSpec("grep", "Grep", JsonNodeFactory.instance.objectNode()), (call, context) -> ToolResult.blocked(call, "unused"))
                .build();
    }
}
