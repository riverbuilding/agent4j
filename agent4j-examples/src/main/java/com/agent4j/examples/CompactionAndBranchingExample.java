package com.agent4j.examples;

import com.agent4j.coding.sdk.CodingAgentRuntime;
import com.agent4j.coding.sdk.CodingAgentSession;
import com.agent4j.coding.sdk.ForkSessionRequest;
import com.agent4j.coding.sdk.PromptResult;
import com.agent4j.core.compaction.CompactionConfig;
import com.agent4j.core.compaction.CompactionResult;

import java.nio.file.Path;
import java.util.Optional;

/** 09-compaction-and-branching: compacts, forks an active path, resumes, and continues a persisted session. */
public final class CompactionAndBranchingExample {
    private CompactionAndBranchingExample() {
    }

    public static void main(String[] args) throws Exception {
        LiveExampleConfiguration configuration = LiveExampleConfiguration.open();
        CodingAgentRuntime runtime = CodingAgentRuntime.create(configuration.toCodingAgentConfig());
        try (runtime) {
            CodingAgentSession session = runtime.createSession("09-compaction-and-branching.jsonl");
            PromptResult first = session.prompt(LiveExampleHelper.buildPromptRequest(
                    "Remember the exact phrase COMPACTION_BRANCH_CONTEXT. Reply with only that phrase.", 0));
            String forkEntryId = session.activeEntryId();
            LiveExampleHelper.printMessage(System.out, first);
            session.prompt(LiveExampleHelper.buildPromptRequest(
                    "Explain in one sentence why preserving the remembered phrase matters for a resumed session.", 0));

            CompactionResult compaction = session.compact(
                    "Preserve the remembered phrase and the reason it matters for continuing this session.",
                    compactionConfig());
            if (!compaction.compacted()) {
                throw new IllegalStateException("the walkthrough compaction did not produce a summary");
            }
            System.out.printf("Compaction tokens: before=%d, after=%d%n",
                    compaction.usageBefore().totalTokens(), compaction.usageAfter().totalTokens());
            System.out.println("Persisted compaction summary: " + compaction.summaryMessage().textContent());

            Path forkFile = runtime.sessionFile("09-compaction-and-branching-fork.jsonl");
            CodingAgentSession fork = runtime.forkSession(
                    new ForkSessionRequest(session, forkFile, Optional.of(forkEntryId)));
            System.out.println("Fork file: " + fork.sessionFile());
            System.out.println("Fork active entry: " + fork.activeEntryId());
            System.out.println("Fork restored messages: " + fork.conversationContext().transcriptMessages().size());

            CodingAgentSession resumed = runtime.resumeSession(session.sessionFile());
            System.out.println("Resumed file: " + resumed.sessionFile());
            System.out.println("Resumed active entry: " + resumed.activeEntryId());
            System.out.println("Resumed messages: " + resumed.conversationContext().transcriptMessages().size());
            PromptResult continued = resumed.prompt(LiveExampleHelper.buildPromptRequest(
                    "What exact phrase should remain available after compaction? Reply with only that phrase.", 0));
            LiveExampleHelper.printMessage(System.out, continued);
            LiveExampleHelper.printUsage(System.out, continued);
        } finally {
            runtime.cleanupOwnedFiles();
        }
    }

    static CompactionConfig compactionConfig() {
        return CompactionConfig.builder()
                .keepTokens(0)
                .keepMessages(2)
                .build();
    }
}
