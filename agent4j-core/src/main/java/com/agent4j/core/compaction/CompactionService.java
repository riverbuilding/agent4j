package com.agent4j.core.compaction;

import com.agent4j.ai.AiAssistantMessage;
import com.agent4j.ai.AiContentBlock;
import com.agent4j.ai.AiModel;
import com.agent4j.ai.AiProvider;
import com.agent4j.ai.AiProviderContext;
import com.agent4j.ai.AiProviderRequest;
import com.agent4j.ai.AiStreamEvent;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.ai.AiTextContent;
import com.agent4j.ai.AiTurnRequest;
import com.agent4j.ai.AiUserMessage;
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

public final class CompactionService {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final CompactionPlanner planner;
    private final CompactionSerializer serializer;
    private final TokenEstimator tokenEstimator;
    private final Clock clock;

    public CompactionService() {
        this(new CompactionPlanner(), new CompactionSerializer(), new ApproximateTokenEstimator(), Clock.systemUTC());
    }

    public CompactionService(
            CompactionPlanner planner,
            CompactionSerializer serializer,
            TokenEstimator tokenEstimator,
            Clock clock
    ) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator, "tokenEstimator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompactionPlan plan(CompactionRequest request, AiModel model) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(model, "model");
        OptionalLong contextWindow = OptionalLong.of(model.contextWindow());
        return planner.plan(preprocessedRequest(request), tokenEstimator, contextWindow);
    }

    public ContextStatus status(CompactionRequest request, AiModel model) {
        return ContextStatus.fromPlan(plan(request, model));
    }

    public CompactionResult compact(
            CompactionRequest request,
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

        OptionalLong contextWindow = OptionalLong.of(model.contextWindow());
        CompactionRequest preparedRequest = preprocessedRequest(request);
        CompactionPlan plan = planner.plan(preparedRequest, tokenEstimator, contextWindow);
        if (!plan.compact()) {
            return CompactionResult.noOp(preparedRequest.reason(), plan.usage());
        }

        String prompt = serializer.buildSummaryPrompt(preparedRequest, plan.prefixMessages());
        String summary = summarize(prompt, provider, model, context, options);
        AgentMessage summaryMessage = summaryMessage(preparedRequest, plan, summary);
        List<AgentMessage> compactedMessages = new java.util.ArrayList<>();
        compactedMessages.add(summaryMessage);
        compactedMessages.addAll(plan.retainedMessages());
        ContextUsage usageAfter = ContextUsage.calculate(
                request.systemPrompt(),
                compactedMessages,
                tokenEstimator,
                contextWindow);
        return new CompactionResult(
                request.reason(),
                summaryMessage,
                plan.retainedMessages(),
                plan.usage(),
                usageAfter);
    }

    private CompactionRequest preprocessedRequest(CompactionRequest request) {
        List<AgentMessage> messages = new CompactionMessagePreprocessor(tokenEstimator)
                .prepare(request.messages(), request.config());
        if (messages == request.messages()) {
            return request;
        }
        return new CompactionRequest(
                request.sessionId(),
                request.reason(),
                messages,
                request.systemPrompt(),
                request.config(),
                request.optionalFocusInstructions().orElse(null));
    }

    static String summarize(
            String prompt,
            AiProvider provider,
            AiModel model,
            AiProviderContext context,
            AiStreamOptions options
    ) throws Exception {
        StringBuilder deltaText = new StringBuilder();
        StringBuilder completedText = new StringBuilder();
        provider.stream(
                new AiProviderRequest(
                        model,
                        new AiTurnRequest(List.of(AiUserMessage.text(prompt)), List.of()),
                        context,
                        options),
                event -> {
                    if (event instanceof AiStreamEvent.TextDelta delta) {
                        deltaText.append(delta.delta());
                    } else if (event instanceof AiStreamEvent.MessageCompleted completed) {
                        completedText.setLength(0);
                        completedText.append(textContent(completed.message()));
                    }
                });
        String summary = !completedText.isEmpty() ? completedText.toString() : deltaText.toString();
        summary = summary.strip();
        return summary.isBlank() ? "(Summary unavailable)" : summary;
    }

    private static String textContent(AiAssistantMessage message) {
        StringBuilder builder = new StringBuilder();
        for (AiContentBlock block : message.content()) {
            if (block instanceof AiTextContent textContent) {
                builder.append(textContent.text());
            }
        }
        return builder.toString();
    }

    private AgentMessage summaryMessage(CompactionRequest request, CompactionPlan plan, String summary) {
        String content = "Here is a summary of the conversation to date:\n\n" + summary;
        ObjectNode metadata = JSON.objectNode()
                .put("reason", request.reason().wireName())
                .put("cutoffIndex", plan.cutoffIndex());
        var retainedEntries = metadata.putArray("retainedEntries");
        plan.retainedMessages().stream()
                .map(AgentMessage::id)
                .forEach(retainedEntries::add);
        return new AgentMessage(
                summaryMessageId(content),
                plan.prefixMessages().isEmpty() ? null : plan.prefixMessages().getLast().id(),
                clock.instant(),
                AgentMessageRole.COMPACTION_SUMMARY,
                ContentBlocks.toJsonArray(List.of(new TextBlock(content, null))),
                metadata);
    }

    private static String summaryMessageId(String content) {
        UUID stableId = UUID.nameUUIDFromBytes(content.getBytes(StandardCharsets.UTF_8));
        return "compaction-summary:" + stableId;
    }
}
