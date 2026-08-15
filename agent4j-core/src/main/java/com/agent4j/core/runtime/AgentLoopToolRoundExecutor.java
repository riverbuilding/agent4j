package com.agent4j.core.runtime;

import com.agent4j.core.event.AgentEvent;
import com.agent4j.core.event.AgentEventBus;
import com.agent4j.core.message.ToolCall;
import com.agent4j.core.message.ToolResult;
import com.agent4j.core.tool.ToolContext;
import com.agent4j.core.tool.ToolExecutionHook;
import com.agent4j.core.tool.ToolExecutor;
import com.agent4j.core.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Executes one model-requested tool round and publishes its tool lifecycle events. */
final class AgentLoopToolRoundExecutor {
    private final ToolExecutor executor;
    private final AgentEventBus eventBus;
    private final List<ToolExecutionHook> hooks;

    AgentLoopToolRoundExecutor(ToolRegistry registry, AgentEventBus eventBus, List<ToolExecutionHook> hooks) {
        this.executor = new ToolExecutor(registry);
        this.eventBus = eventBus;
        this.hooks = hooks == null ? List.of() : List.copyOf(hooks);
    }

    List<ToolResult> execute(AgentLoopRequest request, List<ToolCall> toolCalls) throws Exception {
        if (request.toolExecutionMode() == ToolExecutionMode.SEQUENTIAL || toolCalls.size() <= 1) {
            List<ToolResult> results = new ArrayList<>();
            for (ToolCall toolCall : toolCalls) {
                publishStarted(request, toolCall);
                results.add(executeOne(request, toolCall));
            }
            return results;
        }
        toolCalls.forEach(toolCall -> publishStarted(request, toolCall));
        ExecutorService workers = Executors.newFixedThreadPool(toolCalls.size());
        try {
            List<Future<ToolResult>> futures = toolCalls.stream()
                    .map(toolCall -> workers.submit(() -> executeOne(request, toolCall)))
                    .toList();
            List<ToolResult> results = new ArrayList<>();
            for (Future<ToolResult> future : futures) {
                request.abortSignal().throwIfAborted();
                results.add(await(future));
            }
            return results;
        } finally {
            workers.shutdownNow();
        }
    }

    private void publishStarted(AgentLoopRequest request, ToolCall toolCall) {
        request.abortSignal().throwIfAborted();
        eventBus.publish(new AgentEvent.ToolExecutionStarted(request.sessionId(), request.clock().instant(), toolCall));
    }

    private ToolResult executeOne(AgentLoopRequest request, ToolCall toolCall) throws Exception {
        ToolContext context = new ToolContext(request.sessionId(), request.cwd(), request.clock(), request.abortSignal(),
                request.toolAttributes(), update -> eventBus.publish(new AgentEvent.ToolExecutionUpdated(
                        request.sessionId(), request.clock().instant(), toolCall.id(), update)));
        Optional<ToolResult> blocked = Optional.empty();
        for (ToolExecutionHook hook : hooks) {
            request.abortSignal().throwIfAborted();
            blocked = hook.beforeToolExecution(toolCall, context);
            if (blocked.isPresent()) break;
        }
        ToolResult result = blocked.orElseGet(() -> executor.execute(toolCall, context));
        for (ToolExecutionHook hook : hooks) {
            request.abortSignal().throwIfAborted();
            hook.afterToolExecution(toolCall, context, result);
        }
        return result;
    }

    private static ToolResult await(Future<ToolResult> future) throws Exception {
        try {
            return future.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error throwable) throw throwable;
            throw new IllegalStateException(cause);
        }
    }
}
