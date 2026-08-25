package com.agent4j.agents.buildtriage;

import com.agent4j.core.message.ToolCall;
import com.agent4j.core.operation.ProcessResult;
import com.agent4j.core.runtime.AbortSignal;
import com.agent4j.core.tool.ToolContext;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BuildTriageAgentTest {
    @TempDir
    Path workspace;

    @Test
    void exposesReadOnlyInspectionToolsAndOneFixedMavenTestCommand() {
        var registry = BuildTriageAgent.tools((command, cwd, timeout) -> new ProcessResult(1, "", "failure", Duration.ofSeconds(1), false));

        assertThat(registry.specs()).extracting(spec -> spec.name())
                .containsExactly("read", "grep", "find", "ls", "run_maven_test")
                .doesNotContain("write", "edit", "bash");
    }

    @Test
    void mavenToolRunsOnlyMavenTestAndReportsItsResult() throws Exception {
        AtomicReference<List<String>> command = new AtomicReference<>();
        AtomicReference<Path> cwd = new AtomicReference<>();
        var registry = BuildTriageAgent.tools((arguments, directory, timeout) -> {
            command.set(arguments);
            cwd.set(directory);
            return new ProcessResult(1, "test output", "test failure", Duration.ofMillis(25), false);
        });

        var result = registry.find("run_maven_test").orElseThrow().tool().execute(
                new ToolCall("call-1", "run_maven_test", JsonNodeFactory.instance.objectNode()),
                new ToolContext("session-1", workspace, Clock.systemUTC(), neverAborted(), Map.of()));

        assertThat(command.get()).containsExactly("mvn", "test");
        assertThat(cwd.get()).isEqualTo(workspace);
        assertThat(result.error()).isFalse();
        assertThat(result.content().path("exitCode").asInt()).isEqualTo(1);
        assertThat(result.content().path("stderr").asText()).isEqualTo("test failure");
    }

    @Test
    void systemPromptRequiresEvidenceAndDoesNotAuthorizeMutations() {
        assertThat(BuildTriageAgent.systemPrompt())
                .contains("run_maven_test exactly once", "Root cause", "Evidence", "Do not modify files");
        assertThat(BuildTriageAgent.request("one test failed")).contains("one test failed");
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
