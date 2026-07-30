# ADR 0002: PI Internal Parity Tracking

## Status

Accepted

## Context

ADR 0001 scoped agent4j as a Java port focused primarily on externally
observable PI compatibility. The current direction is stricter: agent4j should
also stay as close as practical to PI's internal harness implementation model.

This does not mean a line-by-line TypeScript translation. It does mean module
boundaries, message shapes, event sequencing, lifecycle hooks, queue semantics,
and conversion points should follow PI unless Java type safety or platform
constraints justify a documented divergence.

## Decision

Each major subsystem must be mapped to its PI source package and behavior before
being marked complete. Intentional divergences must be documented with a reason
and, where practical, a compatibility test.

Primary PI reference packages:

- `@earendil-works/pi-ai`
- `@earendil-works/pi-agent-core`
- `@earendil-works/pi-coding-agent`

Current target version:

- PI coding agent `0.82.x`, currently inspected at `0.82.1`.

## Parity Map

| agent4j area | PI reference | Current status | Notes |
| --- | --- | --- | --- |
| `agent4j-ai` message/content model | `packages/ai/src/types.ts` | Started | Java now has PI-style text, thinking, image, tool-call, user, assistant, and tool-result types. Missing full stream start/end/error protocol and provider options. |
| `agent4j-ai` provider abstraction | `packages/ai/src/types.ts`, `packages/ai/src/providers/*` | Not started | Must mirror PI concepts such as `Context`, `StreamOptions`, `Model`, usage, timeout/retry options, and provider-normalized stream events. |
| `agent4j-core` agent transcript | `packages/agent/src/types.ts` | Started | Java still has an older `AgentMessage` JSON content model, but now has an injectable `convertToLlm` boundary. Needs PI-style `AgentMessage = Message | CustomAgentMessages[...]` equivalent or a documented Java bridge. |
| `agent4j-core` agent loop | `packages/agent/src/agent-loop.ts` | Started | Current loop supports fake text/tool-call turns, emits PI-style start/update/end event boundaries, emits prompt user `message_start`/`message_end` events before the assistant stream, appends tool-result transcript messages in execution order, has prompt/steering/follow-up queue drain semantics, and has initial model retry plus model/tool abort coverage. `turn_end` intentionally repeats the round assistant message and ordered tool-result messages, matching PI summary-event behavior; `agent_end` returns all new messages for the invocation. Needs PI error-stream semantics and parallel/ordered tool-result semantics. |
| `agent4j-core` event model | `packages/agent/src/types.ts` | Started | PI-style discriminators and text/tool-call loop ordering are in place: `agent_start`, `turn_start`, `message_start`, `message_update`, `message_end`, `tool_execution_*`, `turn_end`, `agent_end`. Need payload parity audit for queue, retry, compaction, abort, and tool update details. |
| `agent4j-core` tool abstractions | `packages/agent/src/types.ts` | Started | Basic registry/executor exists. Need PI-style tool execution modes, update callbacks, before/after tool hooks, blocked tool results, and terminate hints. |
| `agent4j-coding` built-in tools | `packages/coding-agent/src/core/tools/*` | External behavior mostly complete | Need audit for argument schemas, descriptions, image/text read behavior, edit multi-replacement behavior, render/update details, and result content/details shape. |
| `agent4j-coding` custom message conversion | `packages/coding-agent/src/core/messages.ts` | Not started | Core exposes the injectable `convertToLlm` boundary. Need Java coding-agent converter implementations for bash execution, custom messages, branch summaries, and compaction summaries. |
| Session JSONL model | `packages/coding-agent/src/core/session-manager.ts`, `packages/agent/src/harness/session/*` | In progress | Existing codec/manager cover core entries and can append Phase 4 `AgentMessage` loop outputs as PI-shaped message entries. Need parent validation, resume/fork stale snapshot semantics, and broader parity fixtures. |
| Resource discovery/settings | `packages/coding-agent/src/core/resource-loader.ts`, `settings-manager.ts`, `skills.ts`, `prompt-templates.ts` | Not started | Must mirror PI discovery order and merge rules before Java-specific conveniences. |
| Compaction | `packages/coding-agent/src/core/compaction/*`, `packages/agent/src/harness/*` | Not started | Must mirror context serialization, cut-point selection, retained tail handling, branch summaries, and overflow retry shape. |
| SDK/runtime API | `packages/coding-agent/src/core/agent-session*.ts`, `sdk.ts` | Not started | Must mirror `AgentSession`, `AgentSessionRuntime`, services, new/resume/fork/clone/import responsibilities. |
| CLI modes | `packages/coding-agent/src/modes/*`, `cli/*` | Not started | Must mirror print, JSON, RPC, session flags, model/tool flags, and event payloads. |
| Extension SPI | `packages/coding-agent/src/core/extensions/*` | Not started | Java SPI should mirror PI hook names and timing first, then document Java-specific differences. |
| Interactive shell/TUI | `packages/coding-agent/src/modes/interactive/*` | Not started | Mirror interaction model, command names, selectors, queue controls, and status semantics before Java terminal rendering choices. |

## Intentional Divergence Log

| Area | Divergence | Reason | Follow-up |
| --- | --- | --- | --- |
| Language/runtime | Java 21 modules instead of TypeScript packages | Project goal is a Java port. | Keep module responsibilities mapped to PI packages. |
| Extension execution | Java SPI first, no native TypeScript extension execution initially | Security and runtime complexity. | Revisit in Phase 12 PI Package Bridge. |
| Tool binary guard | Removed NUL-byte binary rejection from read/edit/grep | PI read decodes non-image files as UTF-8 and does not use this binary rejection. | Add image handling or charset policy only when matching PI behavior. |
| Current AI/core bridge | `agent4j-core.AgentMessage` still stores JSON content while `agent4j-ai` has typed content | Transitional state from early Phase 4. | Core has an explicit `convertToLlm` bridge; add PI-style transcript models and coding-specific converters before Phase 4 completion. |

## Completion Rule

A phase is not complete until:

1. The subsystem is mapped to PI reference files.
2. External behavior tests pass.
3. Internal lifecycle/message/event/tool semantics have been audited.
4. Any divergence is recorded in this ADR or a more specific parity document.

## Consequences

This constraint may slow implementation in the short term because design choices
must be checked against PI internals. The benefit is a Java harness that is
easier to reason about as a PI port, easier to compare during future upgrades,
and less likely to drift into an incompatible agent framework.
