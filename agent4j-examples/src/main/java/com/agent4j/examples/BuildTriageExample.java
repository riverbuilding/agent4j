package com.agent4j.examples;

import com.agent4j.agents.buildtriage.BuildTriageAgent;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CodingAgentSession;
import com.agent4j.coding.sdk.PromptResult;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.EventSubscription;

/** Opt-in live example of a read-only agent that triages one Maven test run. */
public final class BuildTriageExample {
    private static final int MAX_TOOL_ROUNDS = 6;

    private BuildTriageExample() {
    }

    public static void main(String[] args) throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open();
        try {
            if (configuration.temporaryWorkspace()) {
                throw new IllegalStateException("Set " + LiveExampleConfiguration.WORKSPACE
                        + " to the Maven workspace to triage before running this example");
            }
            CodingAgentRuntime runtime = CodingAgentRuntime.create(
                    configuration.toCodingAgentConfig(BuildTriageAgent.tools()));
            try (runtime) {
                CodingAgentSession session = runtime.createSession(
                        runtime.sessionFile("13-build-triage.jsonl"), configuration.workspace());
                System.out.println("Build triage workspace: " + configuration.workspace());
                System.out.println("The agent can read workspace files and run exactly one 'mvn test' command.");
                System.out.println("Review the workspace first: Maven plugins run according to its existing build configuration.");
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
                            BuildTriageAgent.request(String.join(" ", args)),
                            Math.max(configuration.maxToolRounds(), MAX_TOOL_ROUNDS),
                            BuildTriageAgent.systemPrompt()));
                }
                LiveExampleHelper.printMessage(System.out, result);
                LiveExampleHelper.printUsage(System.out, result);
            }
        } finally {
            configuration.cleanupTemporaryDirectories();
        }
    }
}
