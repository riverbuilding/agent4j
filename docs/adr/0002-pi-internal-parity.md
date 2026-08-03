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
| `agent4j-ai` message/content model | `packages/ai/src/types.ts` | Started | Java now has PI-style text, thinking, image, tool-call, user, assistant, and tool-result types plus assistant message/text/thinking/tool-call stream start/delta/end/error records. Missing provider options. |
| `agent4j-ai` provider abstraction | `packages/ai/src/types.ts`, `packages/ai/src/providers/*` | Not started | Must mirror PI concepts such as `Context`, `StreamOptions`, `Model`, usage, timeout/retry options, and provider-normalized stream events. |
| `agent4j-core` agent transcript | `packages/agent/src/types.ts` | Complete for current phase | Java uses `AgentMessage` as the durable JSONL-compatible transcript envelope, with PI-style typed transcript views equivalent to `AgentMessage = Message | CustomAgentMessages[...]` for internal access. Default/coding converters, AgentLoop tool-call/tool-result paths, and prompt inference consume those views. Decision: do not promote the view hierarchy into the primary Java transcript model until broader PI fixture coverage proves unknown-field compatibility. |
| `agent4j-core` agent loop | `packages/agent/src/agent-loop.ts` | Complete for fake-runtime phase, internal message-state parity gap remains | Current loop supports fake text/tool-call turns, emits PI-style start/update/end event boundaries, emits prompt user `message_start`/`message_end` events before the assistant stream, appends tool-result transcript messages in assistant source order, has configurable sequential/parallel tool execution with parallel as the default, has prompt/steering/follow-up queue drain semantics, and has retry success/exhaustion plus model/tool abort coverage. `turn_end` intentionally repeats the round assistant message and ordered tool-result messages, matching PI summary-event behavior; `agent_end` returns all new messages for the invocation. Provider-specific retry classification remains in the provider-adapter phase. PI does not keep separate loop-owned lists equivalent to Java's `modelMessages`, `transcriptMessages`, `newMessages`, and `assistantMessages`: PI keeps the canonical conversation in `AgentState.contextMutable()`, builds a temporary `modelInput`/`ReasoningInput.messages` at the model boundary, accumulates stream chunks in `ReasoningContext`, and returns the final `Msg` through `AgentResultEvent`. Java currently keeps request-local transcript/generated/result lists because `AgentLoop` is separated from `SessionManager`; Phase 8 should close this by introducing a PI-style runtime/session-owned conversation context. |
| `agent4j-core` event model | `packages/agent/src/types.ts` | Complete for Phase 4 | PI-style discriminators and text/tool-call loop ordering are in place: `agent_start`, `turn_start`, `message_start`, `message_update`, `message_end`, `tool_execution_*`, `queue_updated`, `retry_*`, `turn_end`, `agent_end`, and `agent_aborted`. Tool updates can publish `tool_execution_update`, and JSON serialization coverage now pins queue, retry, tool update/end, turn end, agent end, and abort payloads. Compaction event payload parity remains in the later compaction phase. |
| `agent4j-core` tool abstractions | `packages/agent/src/types.ts` | Started | Basic registry/executor exists with sequential/parallel execution modes, `ToolContext` update callbacks, loop-level before/after tool hooks, hook-returned blocked tool results, and terminate hints. |
| `agent4j-coding` built-in tools | `packages/coding-agent/src/core/tools/*` | External behavior mostly complete | Need audit for argument schemas, descriptions, image/text read behavior, edit multi-replacement behavior, render/update details, and result content/details shape. |
| `agent4j-coding` custom message conversion | `packages/coding-agent/src/core/messages.ts` | Started | Core exposes the injectable `convertToLlm` boundary. Java now has a coding-agent converter plus typed wrappers for bash execution, custom messages, branch summaries, and compaction summaries. Needs PI source audit for exact prompt text and any additional custom message variants. |
| Session JSONL model | `packages/coding-agent/src/core/session-manager.ts`, `packages/agent/src/harness/session/*` | In progress | Existing codec/manager cover core entries, can append Phase 4 `AgentMessage` loop outputs as PI-shaped message entries, and can rebuild active-path message entries as `AgentMessage` envelopes for resume. Need parent validation, resume/fork stale snapshot semantics, and broader parity fixtures. |
| Resource discovery/settings | `packages/coding-agent/src/core/resource-loader.ts`, `settings-manager.ts`, `skills.ts`, `prompt-templates.ts` | Complete for resource-loader boundary | Java now has a `ResourceLoader` boundary with typed discovery output, global `~/.pi/agent` and project `.pi` directory resolution, context loading for global/ancestor/current `AGENTS.md` with same-directory `CLAUDE.md` fallback, context-file disable support, project trust gating, project-over-global `SYSTEM.md`, global-then-project `APPEND_SYSTEM.md`, global/project settings loading with nested object merge plus unknown-field preservation, prompt-template discovery from default directories plus settings paths with frontmatter metadata, skill metadata discovery from Pi/Agent Skills directories plus settings paths with diagnostics, theme discovery from default directories/settings/CLI sources, local package resource discovery for prompts/skills/themes from package manifests and conventional directories, resource disable flags, a `SystemPromptBuilder` for ordered system/append/context/skill prompt assembly with hidden disabled skills, core `AiSystemMessage`/`AgentLoopRequest.systemPrompt` transport that keeps system prompts out of persisted transcripts, and `CodingAgentLoopRequestFactory` request preparation that attaches discovered system prompts while returning discovery metadata. npm/git package install/update, interactive trust prompting, trust.json persistence, full coding-session invocation wiring, and exact provider-facing prompt fixture text remain later phase work. |
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
| Current AI/core bridge | `agent4j-core.AgentMessage` still stores JSON content while `agent4j-ai` has typed content | Intentional current-phase design to preserve PI session JSONL round-tripping. | Keep using typed transcript views plus `AgentMessageConverter` as the `convertToLlm` boundary. Revisit promotion only after broader PI fixture coverage. |
| Agent loop message ownership | `AgentLoop` tracks `modelMessages`, `transcriptMessages`, `newMessages`, and `assistantMessages`, while PI's `ReActAgent` mutates `AgentState.contextMutable()` and creates temporary model input only at the model boundary | Current Java loop is intentionally persistence-agnostic: `SessionManager` owns durable state outside the raw loop, so the loop needs request-local collections for model input, result reporting, and persistence handoff. This is more complex than PI internally. | Add a Phase 8 runtime/session API goal to make a PI-style session-owned conversation context the canonical loop state, reducing request-local message lists to boundary/output accumulators. |

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
