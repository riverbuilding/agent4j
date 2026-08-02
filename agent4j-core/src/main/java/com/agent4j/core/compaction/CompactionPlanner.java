package com.agent4j.core.compaction;

import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.message.ContentBlock;
import com.agent4j.core.message.ToolCallBlock;
import com.agent4j.core.message.ToolResultAgentMessageView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

public final class CompactionPlanner {
    public CompactionPlan plan(
            CompactionRequest request,
            TokenEstimator tokenEstimator,
            OptionalLong contextWindowTokens
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(tokenEstimator, "tokenEstimator");
        Objects.requireNonNull(contextWindowTokens, "contextWindowTokens");

        ContextUsage usage = ContextUsage.calculate(
                request.systemPrompt(),
                request.messages(),
                tokenEstimator,
                contextWindowTokens);
        CompactionConfig effectiveConfig = resolveEffectiveConfig(request.config(), contextWindowTokens);
        if (!shouldCompact(request, usage, effectiveConfig)) {
            return CompactionPlan.noOp(request.reason(), usage, effectiveConfig);
        }

        int cutoff = determineCutoffIndex(request.messages(), tokenEstimator, effectiveConfig);
        if (cutoff <= 0 || cutoff >= request.messages().size()) {
            return CompactionPlan.noOp(request.reason(), usage, effectiveConfig);
        }

        return new CompactionPlan(
                true,
                request.reason(),
                cutoff,
                request.messages().subList(0, cutoff),
                request.messages().subList(cutoff, request.messages().size()),
                usage,
                effectiveConfig);
    }

    private static boolean shouldCompact(
            CompactionRequest request,
            ContextUsage usage,
            CompactionConfig effectiveConfig
    ) {
        if (!effectiveConfig.enabled()) {
            return false;
        }
        if (request.reason() == CompactionReason.MANUAL || request.reason() == CompactionReason.OVERFLOW) {
            return true;
        }
        if (effectiveConfig.triggerMessages() > 0 && usage.messageCount() >= effectiveConfig.triggerMessages()) {
            return true;
        }
        return effectiveConfig.triggerTokens() > 0 && usage.totalTokens() >= effectiveConfig.triggerTokens();
    }

    private static CompactionConfig resolveEffectiveConfig(CompactionConfig config, OptionalLong contextWindowTokens) {
        long triggerTokens = config.triggerTokens();
        long keepTokens = config.keepTokens();

        if (config.usesDynamicTrigger()) {
            if (contextWindowTokens.isPresent()) {
                triggerTokens = contextWindowTokens.getAsLong() - config.reservedTokens();
                if (triggerTokens <= 0) {
                    triggerTokens = Math.max(1, contextWindowTokens.getAsLong() / 2);
                }
            } else {
                triggerTokens = CompactionConfig.FALLBACK_TRIGGER_TOKENS;
            }
        }

        if (config.usesDynamicKeepTokens()) {
            if (contextWindowTokens.isPresent()) {
                long usable = Math.max(1, contextWindowTokens.getAsLong() - config.reservedTokens());
                keepTokens = Math.min(
                        config.keepTokensMax(),
                        Math.max(config.keepTokensMin(), (long) (usable * config.keepTokensRatio())));
            } else {
                keepTokens = CompactionConfig.MESSAGE_BASED_KEEP_TOKENS;
            }
        }

        return config.withEffectiveBudgets(triggerTokens, keepTokens);
    }

    private static int determineCutoffIndex(
            List<AgentMessage> messages,
            TokenEstimator tokenEstimator,
            CompactionConfig effectiveConfig
    ) {
        int rawCutoff = effectiveConfig.keepTokens() > 0
                ? findTokenBasedCutoff(messages, tokenEstimator, effectiveConfig.keepTokens())
                : findMessageBasedCutoff(messages, effectiveConfig.keepMessages());
        return findSafeCutoffPoint(messages, rawCutoff);
    }

    private static int findMessageBasedCutoff(List<AgentMessage> messages, int keepMessages) {
        if (messages.isEmpty() || messages.size() <= keepMessages) {
            return 0;
        }
        return messages.size() - keepMessages;
    }

    private static int findTokenBasedCutoff(
            List<AgentMessage> messages,
            TokenEstimator tokenEstimator,
            long keepTokens
    ) {
        if (messages.isEmpty()) {
            return 0;
        }
        int left = 0;
        int right = messages.size();
        int candidate = messages.size();
        while (left < right) {
            int mid = (left + right) / 2;
            long tailTokens = tokenEstimator.estimateMessages(messages.subList(mid, messages.size()));
            if (tailTokens <= keepTokens) {
                candidate = mid;
                right = mid;
            } else {
                left = mid + 1;
            }
        }
        return Math.min(candidate, messages.size() - 1);
    }

    static int findSafeCutoffPoint(List<AgentMessage> messages, int cutoffIndex) {
        if (cutoffIndex <= 0 || cutoffIndex >= messages.size()) {
            return cutoffIndex;
        }

        int cutoff = cutoffIndex;
        while (cutoff < messages.size() && messages.get(cutoff).role() == AgentMessageRole.TOOL_RESULT) {
            List<String> toolCallIds = consecutiveToolResultIds(messages, cutoff);
            if (toolCallIds.isEmpty()) {
                return firstIndexAfterConsecutiveToolResults(messages, cutoff);
            }
            int assistantIndex = findAssistantToolCallIndex(messages, cutoff, toolCallIds);
            if (assistantIndex < 0) {
                return firstIndexAfterConsecutiveToolResults(messages, cutoff);
            }
            cutoff = assistantIndex;
        }
        return cutoff;
    }

    private static List<String> consecutiveToolResultIds(List<AgentMessage> messages, int startIndex) {
        List<String> ids = new ArrayList<>();
        for (int i = startIndex; i < messages.size() && messages.get(i).role() == AgentMessageRole.TOOL_RESULT; i++) {
            String id = ((ToolResultAgentMessageView) messages.get(i).view()).toolCallId();
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static int firstIndexAfterConsecutiveToolResults(List<AgentMessage> messages, int startIndex) {
        int index = startIndex;
        while (index < messages.size() && messages.get(index).role() == AgentMessageRole.TOOL_RESULT) {
            index++;
        }
        return index;
    }

    private static int findAssistantToolCallIndex(
            List<AgentMessage> messages,
            int beforeIndex,
            List<String> toolCallIds
    ) {
        Set<String> ids = new HashSet<>(toolCallIds);
        for (int i = beforeIndex - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (message.role() != AgentMessageRole.ASSISTANT) {
                continue;
            }
            if (assistantCallsAny(message, ids)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean assistantCallsAny(AgentMessage message, Set<String> toolCallIds) {
        for (ContentBlock block : message.contentBlocks()) {
            if (block instanceof ToolCallBlock toolCallBlock && toolCallIds.contains(toolCallBlock.toolCall().id())) {
                return true;
            }
        }
        return false;
    }
}
