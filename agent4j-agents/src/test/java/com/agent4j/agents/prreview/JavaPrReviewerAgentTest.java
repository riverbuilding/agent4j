package com.agent4j.agents.prreview;

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

class JavaPrReviewerAgentTest {
    @TempDir
    Path workspace;

    @Test
    void exposesReadOnlyInspectionToolsAndOneFixedGitDiffCommand() {
        var registry = JavaPrReviewerAgent.tools((command, cwd, timeout) -> new ProcessResult(0, "", "", Duration.ZERO, false));

        assertThat(registry.specs()).extracting(spec -> spec.name())
                .containsExactly("read", "grep", "find", "ls", "read_git_diff")
                .doesNotContain("write", "edit", "bash");
    }

    @Test
    void diffToolReadsTheCombinedTrackedFileDiffRelativeToHead() throws Exception {
        AtomicReference<List<String>> command = new AtomicReference<>();
        AtomicReference<Path> cwd = new AtomicReference<>();
        var registry = JavaPrReviewerAgent.tools((arguments, directory, timeout) -> {
            command.set(arguments);
            cwd.set(directory);
            return new ProcessResult(0, "diff --git a/A.java b/A.java", "", Duration.ofMillis(10), false);
        });

        var result = registry.find("read_git_diff").orElseThrow().tool().execute(
                new ToolCall("call-1", "read_git_diff", JsonNodeFactory.instance.objectNode()),
                new ToolContext("session-1", workspace, Clock.systemUTC(), neverAborted(), Map.of()));

        assertThat(command.get()).containsExactly("git", "diff", "--no-ext-diff", "--unified=80", "HEAD", "--");
        assertThat(cwd.get()).isEqualTo(workspace);
        assertThat(result.error()).isFalse();
        assertThat(result.content().path("stdout").asText()).contains("A.java");
    }

    @Test
    void systemPromptRequiresActionableEvidenceBackedFindings() {
        assertThat(JavaPrReviewerAgent.systemPrompt())
                .contains("read_git_diff exactly once", "[severity] file:line", "Do not modify files");
        assertThat(JavaPrReviewerAgent.request("Focus on concurrency")).contains("Focus on concurrency");
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
