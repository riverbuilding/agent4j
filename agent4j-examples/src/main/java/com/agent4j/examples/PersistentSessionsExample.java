package com.agent4j.examples;

import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CodingAgentSession;
import com.agent4j.coding.sdk.PromptResult;

import java.nio.file.Files;
import java.nio.file.Path;

/** 04-persistent-sessions: persists one real turn, resumes it, and uses its prior conversation context. */
public final class PersistentSessionsExample {
    private PersistentSessionsExample() {
    }

    public static void main(String[] args) throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open();
        CodingAgentRuntime runtime = CodingAgentRuntime.create(configuration.toCodingAgentConfig());
        try (runtime) {
            Path sessionFile = runtime.sessionFile("04-persistent-sessions.jsonl");
            String sessionId = createAndPrompt(runtime, sessionFile);

            System.out.println("Persisted JSONL: " + sessionFile);
            System.out.println("Persisted entries: " + Files.readAllLines(sessionFile).size());

            CodingAgentSession resumed = runtime.resumeSession(sessionFile);
            System.out.println("Resumed session: " + resumed.id());
            System.out.println("Restored messages: " + resumed.conversationContext().transcriptMessages().size());
            if (!sessionId.equals(resumed.id())) {
                throw new IllegalStateException("The resumed session ID did not match the persisted session.");
            }

            PromptResult resumedResult = resumed.prompt(LiveExampleHelper.buildPromptRequest(
                    "What exact phrase did I ask you to remember? Reply with only that phrase.",
                    0));
            LiveExampleHelper.printMessage(System.out, resumedResult);
            LiveExampleHelper.printUsage(System.out, resumedResult);
        } finally {
            runtime.cleanupOwnedFiles();
        }
    }

    private static String createAndPrompt(
            CodingAgentRuntime runtime,
            Path sessionFile
    ) throws Exception {
        CodingAgentSession session = runtime.createSession(sessionFile, runtime.workspace());
        PromptResult initialResult = session.prompt(LiveExampleHelper.buildPromptRequest(
                "Remember the exact phrase PERSISTED_SESSION_READY. Reply with only that phrase.",
                0));
        LiveExampleHelper.printMessage(System.out, initialResult);
        LiveExampleHelper.printUsage(System.out, initialResult);
        return session.id();
    }
}
