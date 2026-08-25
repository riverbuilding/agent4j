package com.agent4j.agents.prreview;

import com.agent4j.coding.tool.CodingToolProfile;
import com.agent4j.coding.tool.CodingTools;
import com.agent4j.coding.tool.LocalProcessOps;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.operation.ProcessOps;
import com.agent4j.core.operation.ProcessResult;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.RegisteredTool;
import com.agent4j.core.tool.ToolRegistry;
import com.agent4j.core.tool.ToolSpec;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Duration;
import java.util.List;

/** Read-only Java pull-request review policy and tool boundary. */
public final class JavaPrReviewerAgent {
    public static final String READ_GIT_DIFF = "read_git_diff";
    public static final Duration GIT_DIFF_TIMEOUT = Duration.ofSeconds(30);
    public static final int MAX_PROCESS_OUTPUT_CHARS = 32_000;

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private JavaPrReviewerAgent() {
    }

    public static String systemPrompt() {
        return """
                You are a Java pull-request reviewer. Work only within the current workspace.
                First call read_git_diff exactly once. Then use read, ls, grep, or find only when needed to verify
                an issue in the changed code and its immediate callers or tests. Do not modify files, run shell
                commands, change dependencies, or report style-only nits.

                Report only actionable correctness, regression, security, concurrency, API-contract, or missing-test
                findings. For each finding use: [severity] file:line — title; explain the concrete failure mode and
                cite the evidence. End with either "No findings" or a short remaining-risk note. Distinguish observed
                facts from inferences. If the diff is empty, report that there is nothing to review.
                """;
    }

    public static String request(String reviewContext) {
        String context = reviewContext == null || reviewContext.isBlank()
                ? "No additional review context was supplied."
                : "Review context:\n" + reviewContext.strip();
        return """
                Review the current Java worktree using the pull-request review policy.
                %s
                """.formatted(context);
    }

    public static ToolRegistry tools() {
        return tools(new LocalProcessOps());
    }

    public static ToolRegistry tools(ProcessOps processOps) {
        ToolRegistry readOnlyTools = CodingTools.localDefaults().registry(CodingToolProfile.READ_ONLY);
        InMemoryToolRegistry.Builder registry = InMemoryToolRegistry.builder();
        for (ToolSpec toolSpec : readOnlyTools.specs()) {
            RegisteredTool tool = readOnlyTools.find(toolSpec.name()).orElseThrow();
            registry.register(tool.spec(), tool.tool());
        }
        registry.register(new ToolSpec(
                        READ_GIT_DIFF,
                        "Reads the current staged and unstaged tracked-file diff relative to HEAD. It cannot accept git arguments or change the worktree.",
                        JSON.objectNode().put("type", "object").set("properties", JSON.objectNode())),
                (call, context) -> readGitDiff(call, context, processOps));
        return registry.build();
    }

    private static ToolResult readGitDiff(ToolCall call, com.agent4j.core.tool.ToolContext context, ProcessOps processOps)
            throws Exception {
        ProcessResult process = processOps.run(
                List.of("git", "diff", "--no-ext-diff", "--unified=80", "HEAD", "--"),
                context.cwd(),
                GIT_DIFF_TIMEOUT);
        var content = JSON.objectNode();
        content.put("command", "git diff --no-ext-diff --unified=80 HEAD --");
        content.put("exitCode", process.exitCode());
        content.put("timedOut", process.timedOut());
        content.put("durationMillis", process.duration().toMillis());
        content.put("stdout", truncate(process.stdout()));
        content.put("stderr", truncate(process.stderr()));
        content.put("outputTruncated", process.stdout().length() > MAX_PROCESS_OUTPUT_CHARS
                || process.stderr().length() > MAX_PROCESS_OUTPUT_CHARS);
        return new ToolResult(call.id(), call.name(), false, content, JSON.objectNode());
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_PROCESS_OUTPUT_CHARS) {
            return value;
        }
        return value.substring(0, MAX_PROCESS_OUTPUT_CHARS) + "\n... output truncated";
    }
}
