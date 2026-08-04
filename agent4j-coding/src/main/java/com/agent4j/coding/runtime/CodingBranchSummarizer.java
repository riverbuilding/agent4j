package com.agent4j.coding.runtime;

import com.agent4j.ai.AiProviderContext;
import com.agent4j.core.compaction.BranchSummaryRequest;
import com.agent4j.core.compaction.BranchSummaryResult;
import com.agent4j.core.compaction.BranchSummaryService;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CodingBranchSummarizer {
    private final BranchSummaryService branchSummaryService;

    public CodingBranchSummarizer() {
        this(new BranchSummaryService());
    }

    public CodingBranchSummarizer(BranchSummaryService branchSummaryService) {
        this.branchSummaryService = Objects.requireNonNull(branchSummaryService, "branchSummaryService");
    }

    public BranchSummaryResult summarizeAndAppend(BranchSummaryGenerationRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        String sourceSessionId = sessionId(request.sourceSessionManager());
        String targetSessionId = sessionId(request.targetSessionManager());
        BranchSummaryResult result = branchSummaryService.summarize(
                new BranchSummaryRequest(
                        sourceSessionId,
                        request.sourceSessionManager().activeAgentMessages(),
                        request.systemPrompt(),
                        request.summaryPrompt(),
                        request.focusInstructions(),
                        request.sourceSessionManager().activeEntryId(),
                        targetSessionId),
                request.selection().provider(),
                request.selection().model(),
                providerContext(request, sourceSessionId, targetSessionId),
                request.options());
        request.targetSessionManager().appendAgentMessage(result.summaryMessage());
        return result;
    }

    private static AiProviderContext providerContext(
            BranchSummaryGenerationRequest request,
            String sourceSessionId,
            String targetSessionId
    ) {
        return new AiProviderContext(
                Optional.of(sourceSessionId),
                Optional.empty(),
                request.optionalCwd(),
                request.auth(),
                request.auth().environment(),
                Map.of(
                        "summaryKind", "branch",
                        "sourceSessionId", sourceSessionId,
                        "targetSessionId", targetSessionId));
    }

    private static String sessionId(com.agent4j.coding.session.SessionManager manager) {
        return manager.document()
                .header()
                .header()
                .map(com.agent4j.coding.session.SessionHeader::id)
                .filter(id -> !id.isBlank())
                .orElse("default");
    }
}
