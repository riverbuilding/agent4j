package com.agent4j.examples;

import com.agent4j.core.message.ToolCall;
import com.agent4j.core.runtime.AbortSignal;
import com.agent4j.core.tool.ToolContext;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceStatusToolTest {
    @TempDir
    Path workspace;

    @Test
    void reportsTheWorkspaceWithoutFilesystemOrProcessCapabilities() throws Exception {
        var registry = WorkspaceStatusTool.registry();
        var registered = registry.find("workspace_status").orElseThrow();

        var result = registered.tool().execute(
                new ToolCall("call-1", "workspace_status", JsonNodeFactory.instance.objectNode()),
                new ToolContext("session-1", workspace, Clock.systemUTC(), neverAborted(), java.util.Map.of()));

        assertThat(registry.specs()).extracting(spec -> spec.name()).containsExactly("workspace_status");
        assertThat(result.error()).isFalse();
        assertThat(result.content().path("workspace").asText()).isEqualTo(workspace.toString());
        assertThat(result.content().path("sideEffects").asText()).isEqualTo("none");
    }

    private static AbortSignal neverAborted() {
        return new AbortSignal() {
            @Override
            public boolean aborted() {
                return false;
            }

            @Override
            public Optional<String> reason() {
                return Optional.empty();
            }
        };
    }
}
