package com.agent4j.examples;

import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CodingAgentSession;
import com.agent4j.coding.sdk.PromptResult;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.EventSubscription;

/** 03-tool-calling: invokes one no-side-effect workspace-status tool during a real prompt. */
public final class ToolCallingExample {
    private ToolCallingExample() {
    }

    public static void main(String[] args) throws Exception {
        try (LiveExampleConfiguration configuration = LiveExampleConfiguration.open()) {
            CodingAgentRuntime runtime = configuration.createRuntime(WorkspaceStatusTool.registry());
            CodingAgentSession session = runtime.createSession(
                    configuration.sessionFile("03-tool-calling.jsonl"), configuration.workspace());
            PromptResult result;
            try (EventSubscription ignored = runtime.subscribe(event -> {
                if (event instanceof AgentEvent.ToolExecutionStarted started) {
                    System.out.println("Tool started: " + started.toolCall().name());
                }
                if (event instanceof AgentEvent.ToolExecutionEnded ended) {
                    System.out.println("Tool result: " + ended.result().content());
                }
            })) {
                result = session.prompt(LiveExampleSupport.prompt(
                        runtime,
                        "Call the workspace_status tool exactly once, then state the reported workspace path. "
                                + "Do not request any other tool.",
                        1));
            }
            if (result.loopResult().toolResults().isEmpty()) {
                throw new IllegalStateException("The selected model did not invoke workspace_status.");
            }
            LiveExampleSupport.printMessage(System.out, result);
            LiveExampleSupport.printUsage(System.out, result);
        }
    }
}
