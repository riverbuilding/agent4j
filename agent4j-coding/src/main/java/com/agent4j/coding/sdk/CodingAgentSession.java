package com.agent4j.coding.sdk;

import com.agent4j.ai.AiProviderRegistry;
import com.agent4j.ai.AiProviderSelection;
import com.agent4j.ai.AiResolvedAuth;
import com.agent4j.ai.AiStreamOptions;
import com.agent4j.coding.runtime.ManualCompactionRequest;
import com.agent4j.coding.session.SessionEntry;
import com.agent4j.coding.session.SessionHeader;
import com.agent4j.coding.session.SessionManager;
import com.agent4j.core.compaction.CompactionConfig;
import com.agent4j.core.compaction.CompactionResult;
import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.message.AgentMessage;
import com.agent4j.core.message.AgentMessageRole;
import com.agent4j.core.runtime.AbortController;
import com.agent4j.core.runtime.AbortSignal;
import com.agent4j.core.runtime.AgentConversationContext;
import com.agent4j.core.runtime.AgentLoop;
import com.agent4j.core.runtime.AgentLoopOptions;
import com.agent4j.core.runtime.AgentLoopRequest;
import com.agent4j.core.runtime.AgentLoopResult;
import com.agent4j.core.runtime.AgentMessageConverter;
import com.agent4j.core.runtime.LiveAgentQueues;
import com.agent4j.core.runtime.QueueKind;
import com.agent4j.core.tool.ToolRegistry;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class CodingAgentSession implements AgentSession {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final CodingAgentRuntime runtime;
    private final SessionManager sessionManager;
    private AgentConversationContext conversationContext;
    private final AtomicReference<ActivePrompt> activePrompt = new AtomicReference<>();

    CodingAgentSession(
            CodingAgentRuntime runtime,
            SessionManager sessionManager,
            AgentConversationContext conversationContext
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.conversationContext = Objects.requireNonNull(conversationContext, "conversationContext");
    }

    @Override
    public AgentSessionInfo info() {
        SessionHeader header = sessionManager.document().header().header()
                .orElseThrow(() -> new IllegalStateException("session header is missing"));
        return new AgentSessionInfo(header.id(), sessionManager.sessionFile(), cwd(header), sessionManager.activeEntryId());
    }

    @Override
    public AgentConversationContext conversationContext() {
        return conversationContext;
    }

    @Override
    public PromptResult prompt(PromptRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        AbortController abortController = new AbortController();
        LiveAgentQueues queues = new LiveAgentQueues(request.steeringMessages(), request.followUpMessages());
        ActivePrompt active = beginPrompt(abortController, queues);
        PromptResult result;
        try {
            AgentMessage promptMessage = promptMessage(request);
            List<AgentMessage> messages = java.util.stream.Stream
                    .concat(sessionManager.activeAgentMessages().stream(), java.util.stream.Stream.of(promptMessage))
                    .toList();
            AgentLoopResult loopResult = agentLoop(request).runTurn(loopRequest(
                    request, promptMessage, messages, abortSignal(request, abortController), queues));
            List<SessionEntry> persistedEntries = sessionManager.appendAgentLoopResult(loopResult);
            result = new PromptResult(this, loopResult, persistedEntries);
        } finally {
            endPrompt(active);
        }
        refreshConversationContext(new AgentConversationContext(
                sessionManager.activeAgentMessages(), result.loopResult().messages()));
        return result;
    }

    @Override public boolean isStreaming() { return activePrompt.get() != null; }
    @Override public int pendingMessageCount() {
        ActivePrompt active = activePrompt.get();
        return active == null ? 0 : active.queues().size(QueueKind.STEER) + active.queues().size(QueueKind.FOLLOW_UP);
    }
    @Override public void steer(String message) { queue(message, true); }
    @Override public void followUp(String message) { queue(message, false); }
    @Override public boolean abort(String reason) {
        ActivePrompt active = activePrompt.get();
        return active != null && active.abortController().abort(reason);
    }

    @Override
    public CompactionResult compact(String focusInstructions) throws Exception {
        if (isStreaming()) {
            throw new IllegalStateException("cannot compact while a prompt is active");
        }
        AiProviderSelection selection = runtime.optionalProviderRegistry()
                .orElseThrow(() -> new IllegalStateException("manual compaction requires a provider registry"))
                .requireDefault();
        AiResolvedAuth auth = runtime.loginService().resolveAuth(selection.provider().id());
        CompactionResult result = runtime.sessionCompactor().compact(new ManualCompactionRequest(
                sessionManager, selection, auth, cwd(), null, CompactionConfig.defaults(),
                focusInstructions == null ? "" : focusInstructions, AiStreamOptions.defaults()));
        refreshConversationContext(new AgentConversationContext(sessionManager.activeAgentMessages(), List.of()));
        return result;
    }

    private void queue(String text, boolean steering) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("queued message must not be blank");
        }
        ActivePrompt active = requireActivePrompt();
        AgentMessage message = new AgentMessage(messageId(), sessionManager.activeEntryId(), Instant.now(runtime.clock()),
                AgentMessageRole.USER, JSON.textNode(text.strip()), JSON.objectNode());
        QueueKind kind = steering ? QueueKind.STEER : QueueKind.FOLLOW_UP;
        if (steering) {
            active.queues().steer(message);
        } else {
            active.queues().followUp(message);
        }
        runtime.eventBus().publish(new AgentEvent.QueueUpdated(
                id(), Instant.now(runtime.clock()), kind, active.queues().size(kind)));
    }

    private AgentLoop agentLoop(PromptRequest request) {
        ToolRegistry toolRegistry = runtime.toolRegistry();
        AgentMessageConverter messageConverter = runtime.messageConverter();
        AiProviderSelection selection = providerSelection(request);
        AiResolvedAuth auth = runtime.loginService().resolveAuth(selection.provider().id());
        return new AgentLoop(selection, auth, toolRegistry, runtime.eventBus(), messageConverter);
    }

    private AiProviderSelection providerSelection(PromptRequest request) {
        AiProviderRegistry registry = runtime.optionalProviderRegistry()
                .orElseThrow(() -> new IllegalStateException("provider registry is not configured"));
        return request.model().map(registry::require).orElseGet(registry::requireDefault);
    }

    private AgentLoopRequest loopRequest(
            PromptRequest request,
            AgentMessage promptMessage,
            List<AgentMessage> messages,
            AbortSignal abortSignal,
            LiveAgentQueues queues
    ) {
        return new AgentLoopRequest(
                id(),
                turnId(),
                promptMessage.id(),
                messages,
                cwd(),
                runtime.clock(),
                abortSignal,
                AgentLoopOptions.builder()
                        .toolAttributes(request.toolAttributes())
                        .maxToolRounds(request.maxToolRounds())
                        .maxModelRetries(request.maxModelRetries())
                        .modelTimeout(request.modelTimeout())
                        .toolExecutionMode(request.toolExecutionMode())
                        .promptMessages(List.of(promptMessage))
                        .steeringMode(request.steeringMode())
                        .followUpMode(request.followUpMode())
                        .systemPrompt(request.systemPrompt().orElse(null))
                        .build(),
                queues);
    }

    private AgentMessage promptMessage(PromptRequest request) {
        return new AgentMessage(
                messageId(), sessionManager.activeEntryId(), Instant.now(runtime.clock()), AgentMessageRole.USER,
                JSON.textNode(request.prompt()), JSON.objectNode());
    }

    @Override
    public Path cwd() {
        return cwd(sessionManager.document().header().header()
                .orElseThrow(() -> new IllegalStateException("session header is missing")));
    }

    private static Path cwd(SessionHeader header) {
        if (header.cwd() == null || header.cwd().isBlank()) {
            throw new IllegalStateException("session header cwd is missing");
        }
        return Path.of(header.cwd());
    }

    private void refreshConversationContext(AgentConversationContext conversationContext) {
        this.conversationContext = Objects.requireNonNull(conversationContext, "conversationContext");
    }

    private ActivePrompt beginPrompt(AbortController abortController, LiveAgentQueues queues) {
        ActivePrompt active = new ActivePrompt(abortController, queues);
        if (!activePrompt.compareAndSet(null, active)) {
            throw new IllegalStateException("a prompt is already active for this session");
        }
        return active;
    }

    private void endPrompt(ActivePrompt active) {
        activePrompt.compareAndSet(active, null);
    }

    private ActivePrompt requireActivePrompt() {
        ActivePrompt active = activePrompt.get();
        if (active == null) {
            throw new IllegalStateException("no prompt is active for this session");
        }
        return active;
    }

    private static AbortSignal abortSignal(PromptRequest request, AbortController local) {
        return new AbortSignal() {
            @Override public boolean aborted() {
                return local.signal().aborted() || request.abortSignal().map(AbortSignal::aborted).orElse(false);
            }

            @Override public java.util.Optional<String> reason() {
                return local.signal().reason().or(() -> request.abortSignal().flatMap(AbortSignal::reason));
            }
        };
    }

    private static String messageId() {
        return "msg-" + UUID.randomUUID();
    }

    private static String turnId() {
        return "turn-" + UUID.randomUUID();
    }

    private record ActivePrompt(AbortController abortController, LiveAgentQueues queues) {
    }
}
