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

/** 06-resources-and-coding-tools: discovers sample resources and uses workspace-scoped read-only coding tools. */
public final class ResourcesAndCodingToolsExample {
    private static final List<String> READ_ONLY_TOOLS = List.of("read", "ls", "grep", "find");

    private ResourcesAndCodingToolsExample() {
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
                    runtime.sessionFile("06-resources-and-coding-tools.jsonl"), sampleWorkspace.path());
            System.out.println("Workspace: " + sampleWorkspace.path());
            System.out.println("Discovered settings: " + discovery.settings().values());
            System.out.println("Context files: " + discovery.contextFiles().stream().map(file -> file.path().getFileName()).toList());
            System.out.println("Read-only tools: " + READ_ONLY_TOOLS);
            PromptResult result;
            try (EventSubscription ignored = runtime.subscribe(event -> {
                if (event instanceof AgentEvent.ToolExecutionStarted started) {
                    System.out.println("Tool started: " + started.toolCall().name());
                }
                if (event instanceof AgentEvent.ToolExecutionEnded ended) {
                    System.out.println("Tool result: " + ended.result().content());
                }
            })) {
                result = session.prompt(LiveExampleHelper.buildToolRequiredPromptRequest(
                        "Call the read tool exactly once with path README.md. Then report the project name and explain "
                                + "that the available tools cannot write files or run commands outside this workspace.",
                        configuration.maxToolRounds(),
                        systemPrompt));
            }
            if (result.loopResult().toolResults().isEmpty()) {
                throw new IllegalStateException("The selected model did not invoke the read-only workspace tool.");
            }
            LiveExampleHelper.printMessage(System.out, result);
            LiveExampleHelper.printUsage(System.out, result);
        } finally {
            runtime.cleanupOwnedFiles();
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
