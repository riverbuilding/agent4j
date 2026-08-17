package com.agent4j.examples;

import com.agent4j.coding.resource.ResourceDiscovery;
import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CodingAgentSession;
import com.agent4j.coding.sdk.PromptResult;
import com.agent4j.coding.tool.CodingTools;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.EventSubscription;
import com.agent4j.core.tool.InMemoryToolRegistry;
import com.agent4j.core.tool.RegisteredTool;
import com.agent4j.core.tool.ToolRegistry;

import java.util.List;
import java.util.UUID;

/** 12-reference-application: a small, safe workspace coding assistant. */
public final class ReferenceApplicationExample {
    private static final List<String> READ_ONLY_TOOLS = List.of("read", "ls", "grep", "find");

    private ReferenceApplicationExample() {
    }

    public static void main(String[] args) throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open();
        ResourcesAndCodingToolsWorkspace.SampleWorkspace sampleWorkspace =
                ResourcesAndCodingToolsWorkspace.createAndDiscover(configuration.workspace());
        ResourceDiscovery discovery = sampleWorkspace.discovery();
        String systemPrompt = ResourcesAndCodingToolsWorkspace.systemPrompt(discovery);
        CodingAgentRuntime runtime = CodingAgentRuntime.create(configuration.toCodingAgentConfig(readOnlyTools()));
        try (runtime) {
            CodingAgentSession session = runtime.createSession(
                    runtime.sessionFile("12-reference-application-" + UUID.randomUUID() + ".jsonl"), sampleWorkspace.path());
            System.out.println("Workspace coding assistant");
            System.out.println("Workspace: " + sampleWorkspace.path());
            System.out.println("Session JSONL: " + session.sessionFile());
            System.out.println("Read-only tools: " + READ_ONLY_TOOLS);
            System.out.println("Streaming response:");

            PromptResult result;
            try (EventSubscription ignored = runtime.subscribe(event -> render(event))) {
                result = session.prompt(LiveExampleHelper.buildToolRequiredPromptRequest(
                        request(args), configuration.maxToolRounds(), systemPrompt));
            }
            if (result.loopResult().toolResults().isEmpty()) {
                throw new IllegalStateException("The selected model did not invoke a read-only workspace tool.");
            }
            System.out.println();
            LiveExampleHelper.printUsage(System.out, result);
        } finally {
            runtime.cleanupOwnedFiles();
        }
    }

    private static String request(String[] args) {
        String userRequest = args.length == 0
                ? "Read README.md exactly once, then identify the project and summarize the purpose of src/Library.java."
                : String.join(" ", args).strip();
        return "Act as a read-only coding assistant. Use an available tool before answering. "
                + "Do not claim to have changed files or run commands. User request: " + userRequest;
    }

    private static void render(AgentEvent event) {
        if (event instanceof AgentEvent.MessageUpdated updated
                && "text_delta".equals(updated.delta().path("type").asText())) {
            System.out.print(updated.delta().path("delta").asText());
            System.out.flush();
        }
        if (event instanceof AgentEvent.ToolExecutionStarted started) {
            System.out.println("\n[tool started: " + started.toolCall().name() + "]");
        }
        if (event instanceof AgentEvent.ToolExecutionEnded ended) {
            System.out.println("[tool " + (ended.result().error() ? "failed: " : "completed: ")
                    + ended.result().toolName() + "]");
        }
    }

    private static ToolRegistry readOnlyTools() {
        ToolRegistry available = CodingTools.localDefaults().registry();
        InMemoryToolRegistry.Builder selected = InMemoryToolRegistry.builder();
        for (String name : READ_ONLY_TOOLS) {
            RegisteredTool tool = available.find(name).orElseThrow();
            selected.register(tool.spec(), tool.tool());
        }
        return selected.build();
    }
}
