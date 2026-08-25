package com.agent4j.examples;

import com.agent4j.agents.prreview.JavaPrReviewerAgent;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CodingAgentSession;
import com.agent4j.coding.sdk.PromptResult;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.EventSubscription;

/** Opt-in live example of a read-only Java pull-request reviewer. */
public final class JavaPrReviewerExample {
    private static final int MAX_TOOL_ROUNDS = 8;

    private JavaPrReviewerExample() {
    }

    public static void main(String[] args) throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open();
        try {
            if (configuration.temporaryWorkspace()) {
                throw new IllegalStateException("Set " + LiveExampleConfiguration.WORKSPACE
                        + " to the Git workspace to review before running this example");
            }
            CodingAgentRuntime runtime = CodingAgentRuntime.create(
                    configuration.toCodingAgentConfig(JavaPrReviewerAgent.tools()));
            try (runtime) {
                CodingAgentSession session = runtime.createSession(
                        runtime.sessionFile("14-java-pr-review.jsonl"), configuration.workspace());
                System.out.println("Java PR review workspace: " + configuration.workspace());
                System.out.println("The agent can inspect tracked-file changes and read workspace files; it cannot modify the worktree.");
                PromptResult result;
                try (EventSubscription ignored = runtime.subscribe(event -> {
                    if (event instanceof AgentEvent.ToolExecutionStarted started) {
                        System.out.println("Tool started: " + started.toolCall().name());
                    }
                    if (event instanceof AgentEvent.ToolExecutionEnded ended) {
                        System.out.println("Tool completed: " + ended.result().toolName());
                    }
                })) {
                    result = session.prompt(LiveExampleHelper.buildToolRequiredPromptRequest(
                            JavaPrReviewerAgent.request(String.join(" ", args)),
                            Math.max(configuration.maxToolRounds(), MAX_TOOL_ROUNDS),
                            JavaPrReviewerAgent.systemPrompt()));
                }
                LiveExampleHelper.printMessage(System.out, result);
                LiveExampleHelper.printUsage(System.out, result);
            }
        } finally {
            configuration.cleanupTemporaryDirectories();
        }
    }
}
