# Tool Hook Timing Audit

Phase 8 pins the current Java tool-hook timing before the later extension SPI.

## Runtime Ownership

`ToolExecutionHook` is owned by `AgentLoop`, not by each
`AgentLoopRequest`. This matches the current PI-parity direction: tool hooks are
runtime/session capabilities, while a request carries invocation inputs and
queue/config state.

## Timing Contract

For each tool call:

1. `AgentLoop` publishes `tool_execution_start`.
2. `beforeToolExecution(...)` hooks run in registration order.
3. If a before hook returns a `ToolResult`, the real tool executor is skipped.
4. Otherwise the registered tool executor runs.
5. `afterToolExecution(...)` hooks run in registration order with the final
   result, including blocked results.
6. `AgentLoop` publishes `tool_execution_end`.
7. The tool result is converted to a `toolResult` transcript message and emitted
   with `message_start` / `message_end`.

Hooks may publish `tool_execution_update` events through `ToolContext`.

## Parallel Mode

When multiple tool calls run in parallel, `AgentLoop` publishes all
`tool_execution_start` events before submitting tool work. Results are collected
in source order for transcript emission, so model-visible tool-result messages
remain deterministic even if tool execution completes out of order.

## Blocked Results

A before hook can return `ToolResult.blocked(...)`. That result follows the same
after-hook, `tool_execution_end`, and transcript-message path as a normal tool
result. The executor itself is not invoked.

## Remaining PI Audit

The timing is pinned for the current Java runtime. Later extension SPI work still
needs exact PI extension hook names, registration/discovery shape, and whether
hook exceptions should be surfaced as tool results or agent failures.
