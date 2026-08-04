package com.agent4j.core.compaction;

import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderContext;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlocks;
import com.agent4j.core.message.TextBlock;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

public final class BranchSummaryService {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final CompactionSerializer serializer;
    private final TokenEstimator tokenEstimator;
    private final Clock clock;

    public BranchSummaryService() {
        this(new CompactionSerializer(), new ApproximateTokenEstimator(), Clock.systemUTC());
    }

    public BranchSummaryService(CompactionSerializer serializer, TokenEstimator tokenEstimator, Clock clock) {
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator, "tokenEstimator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BranchSummaryResult summarize(
            BranchSummaryRequest request,
            AiProvider provider,
            AiModel model,
            AiProviderContext context,
            AiStreamOptions options
    ) throws Exception {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        context = context == null ? AiProviderContext.empty() : context;
        options = options == null ? AiStreamOptions.defaults() : options;

        ContextUsage usageBefore = ContextUsage.calculate(
                request.systemPrompt(),
                request.messages(),
                tokenEstimator,
                OptionalLong.of(model.contextWindow()));
        String prompt = serializer.buildBranchSummaryPrompt(request);
        String summary = CompactionService.summarize(prompt, provider, model, context, options);
        return new BranchSummaryResult(summaryMessage(request, summary), usageBefore);
    }

    private AgentMessage summaryMessage(BranchSummaryRequest request, String summary) {
        String content = "Here is a summary of the source conversation branch:\n\n" + summary;
        ObjectNode metadata = JSON.objectNode()
                .put("summaryKind", "branch")
                .put("sourceMessageCount", request.messages().size());
        request.optionalSourceEntryId().ifPresent(value -> metadata.put("sourceEntryId", value));
        request.optionalTargetSessionId().ifPresent(value -> metadata.put("targetSessionId", value));
        var sourceEntries = metadata.putArray("sourceEntries");
        request.messages().stream()
                .map(AgentMessage::id)
                .forEach(sourceEntries::add);
        return new AgentMessage(
                summaryMessageId(content),
                request.messages().isEmpty() ? null : request.messages().getLast().id(),
                clock.instant(),
                AgentMessageRole.BRANCH_SUMMARY,
                ContentBlocks.toJsonArray(List.of(new TextBlock(content, null))),
                metadata);
    }

    private static String summaryMessageId(String content) {
        UUID stableId = UUID.nameUUIDFromBytes(content.getBytes(StandardCharsets.UTF_8));
        return "branch-summary:" + stableId;
    }
}
