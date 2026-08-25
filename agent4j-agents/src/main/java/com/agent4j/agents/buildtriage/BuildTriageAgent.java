package com.agent4j.agents.buildtriage;

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

/** Read-only Maven test failure triage policy and tool boundary. */
public final class BuildTriageAgent {
    public static final String RUN_MAVEN_TEST = "run_maven_test";
    public static final Duration MAVEN_TEST_TIMEOUT = Duration.ofMinutes(5);
    public static final int MAX_PROCESS_OUTPUT_CHARS = 24_000;

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private BuildTriageAgent() {
    }

    public static String systemPrompt() {
        return """
                You are a Java build-failure triager. Work only within the current workspace.
                First call run_maven_test exactly once. Then use read, ls, grep, or find only when needed to
                identify the root cause. Do not modify files, run shell commands, change dependencies, or suggest
                commands that mutate the repository.

                Return these sections: Root cause, Evidence, Smallest viable fix, and Verification.
                Distinguish observed facts from inferences. If the Maven command succeeds, say that no build failure
                was reproduced and summarize any user-provided failure context without inventing a diagnosis.
                """;
    }

    public static String request(String reportedFailure) {
        String failureContext = reportedFailure == null || reportedFailure.isBlank()
                ? "No additional failure output was supplied."
                : "User-provided failure context:\n" + reportedFailure.strip();
        return """
                Triage the current Maven workspace using the build-triage policy.
                %s
                """.formatted(failureContext);
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
                        RUN_MAVEN_TEST,
                        "Runs exactly 'mvn test' in the workspace. It cannot accept shell arguments; Maven plugins run according to the workspace configuration.",
                        JSON.objectNode().put("type", "object").set("properties", JSON.objectNode())),
                (call, context) -> runMavenTest(call, context, processOps));
        return registry.build();
    }

    private static ToolResult runMavenTest(ToolCall call, com.agent4j.core.tool.ToolContext context, ProcessOps processOps)
            throws Exception {
        ProcessResult process = processOps.run(List.of("mvn", "test"), context.cwd(), MAVEN_TEST_TIMEOUT);
        var content = JSON.objectNode();
        content.put("command", "mvn test");
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
