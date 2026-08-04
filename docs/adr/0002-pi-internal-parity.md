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
| `agent4j-ai` message/content model | `packages/ai/src/types.ts` | Complete for Phase 6, Phase 8 provider metadata baseline complete | Java now has PI-style text, thinking, image, tool-call, user, assistant, system, and tool-result types plus assistant message/text/thinking/tool-call stream start/delta/end/error records. Phase 8 adds `AiModelFeatures` as the typed provider/model capability surface around this message model. |
| `agent4j-ai` provider abstraction | `packages/ai/src/types.ts`, `packages/ai/src/providers/*` | Complete for Phase 8 provider gap baseline | Java now has `AiProvider`, `AiProviderRequest`, `AiProviderContext`, `AiStreamOptions`, request hooks, model metadata, registry/selection, auth-store abstractions, OpenAI Responses and Anthropic Messages adapters, fake/recorded contract tests, timeout transport, and normalized usage/stream events. Phase 8 provider-options baseline is started with `AiGenerationOptions`, carrying common max-output, temperature, top-p, top-k, tool-choice, parallel-tool-call, and metadata settings into OpenAI/Anthropic request serialization. Timeout/retry ownership is now pinned: `httpIdleTimeoutMs`/`AgentLoopRequest.modelTimeout` flows to provider transport request timeout, `AgentLoopRequest.maxModelRetries` owns model-round retry count, provider calls issued by `AgentLoop` use `AiStreamOptions.maxRetries = 0` to avoid nested adapter retries, and `AiRetryClassifier`/`AiProviderHttpException` classify transient provider failures. Provider/model feature flags are represented by `AiProviderFeatures` and `AiModelFeatures`; adapters expose provider flags and honor model/provider tool-calling, tool-choice, and parallel-tool-call support when serializing requests. Endpoint/base-url parity is pinned: `AiProviderRequest` applies auth `baseUrl` to a request-scoped effective model, while OpenAI/Anthropic adapters resolve endpoints from `request.model().baseUrl()` plus provider path suffixes. Auth-mode readiness is pinned with `AiAuthMode`, access-token/subscription-ready `AiResolvedAuth` fields, OpenAI bearer access-token support, and settings parsing for `authMode`, `accessToken`, `expiresAt`, and auth metadata. Phase 9 owns the live ChatGPT/Codex subscription login flow itself. |
| `agent4j-core` agent transcript | `packages/agent/src/types.ts` | Complete for current phase | Java uses `AgentMessage` as the durable JSONL-compatible transcript envelope, with PI-style typed transcript views equivalent to `AgentMessage = Message | CustomAgentMessages[...]` for internal access. Default/coding converters, AgentLoop tool-call/tool-result paths, and prompt inference consume those views. Decision: do not promote the view hierarchy into the primary Java transcript model until broader PI fixture coverage proves unknown-field compatibility. |
| `agent4j-core` agent loop | `packages/agent/src/agent-loop.ts` | Complete through Phase 8 message-state reduction slice | Current loop supports fake and provider-backed text/tool-call turns, emits PI-style start/update/end event boundaries, emits prompt user `message_start`/`message_end` events before the assistant stream, appends tool-result transcript messages in assistant source order, has configurable sequential/parallel tool execution with parallel as the default, has prompt/steering/follow-up queue drain semantics, and has retry success/exhaustion plus model/tool abort coverage. `turn_end` intentionally repeats the round assistant message and ordered tool-result messages, matching PI summary-event behavior; `agent_end` returns all new messages for the invocation. Java now uses `AgentConversationContext` as the canonical mutable conversation context for model-visible transcript plus generated messages. Provider-facing `AiMessage` input is rebuilt at the model boundary, and assistant-only output is derived from generated messages rather than tracked as separate loop-owned state. |
| `agent4j-core` event model | `packages/agent/src/types.ts` | Complete through Phase 7, payload audit pending | PI-style discriminators and text/tool-call loop ordering are in place: `agent_start`, `turn_start`, `message_start`, `message_update`, `message_end`, `tool_execution_*`, `queue_updated`, `retry_*`, `compaction_*`, `turn_end`, `agent_end`, and `agent_aborted`. Tool updates can publish `tool_execution_update`, and JSON serialization coverage pins queue, retry, tool update/end, turn end, agent end, and abort payloads. Remaining gap is a final PI payload audit for compaction/branch-summary events. |
| `agent4j-core` tool abstractions | `packages/agent/src/types.ts` | Started, Phase 8 audit needed | Basic registry/executor exists with sequential/parallel execution modes, `ToolContext` update callbacks, loop-level before/after tool hooks, hook-returned blocked tool results, and terminate hints. Need exact PI hook name/timing audit before extension-facing APIs are added. |
| `agent4j-coding` built-in tools | `packages/coding-agent/src/core/tools/*` | External behavior mostly complete, Phase 8 audit needed | Need audit for argument schemas, descriptions, image/text read behavior, edit multi-replacement behavior, render/update details, and result content/details shape. |
| `agent4j-coding` custom message conversion | `packages/coding-agent/src/core/messages.ts` | Started, Phase 8 audit needed | Core exposes the injectable `convertToLlm` boundary. Java now has a coding-agent converter plus typed wrappers for bash execution, custom messages, branch summaries, and compaction summaries. Needs PI source audit for exact prompt text and any additional custom message variants. |
| Session JSONL model | `packages/coding-agent/src/core/session-manager.ts`, `packages/agent/src/harness/session/*` | In progress, Phase 8 fix target | Existing codec/manager cover core entries, can append Phase 4+ `AgentMessage` loop outputs as PI-shaped message entries, rebuild active-path message entries as `AgentMessage` envelopes for resume, persist compaction summaries/retained tail entries, and append branch summaries. Need stronger parent validation, malformed typed payload validation, stale snapshot/multi-process resume semantics, and broader parity fixtures. |
| Resource discovery/settings | `packages/coding-agent/src/core/resource-loader.ts`, `settings-manager.ts`, `skills.ts`, `prompt-templates.ts` | Complete for resource-loader boundary | Java now has a `ResourceLoader` boundary with typed discovery output, global `~/.pi/agent` and project `.pi` directory resolution, context loading for global/ancestor/current `AGENTS.md` with same-directory `CLAUDE.md` fallback, context-file disable support, project trust gating, project-over-global `SYSTEM.md`, global-then-project `APPEND_SYSTEM.md`, global/project settings loading with nested object merge plus unknown-field preservation, prompt-template discovery from default directories plus settings paths with frontmatter metadata, skill metadata discovery from Pi/Agent Skills directories plus settings paths with diagnostics, theme discovery from default directories/settings/CLI sources, local package resource discovery for prompts/skills/themes from package manifests and conventional directories, resource disable flags, a `SystemPromptBuilder` for ordered system/append/context/skill prompt assembly with hidden disabled skills, core `AiSystemMessage`/`AgentLoopRequest.systemPrompt` transport that keeps system prompts out of persisted transcripts, and `CodingAgentLoopRequestFactory` request preparation that attaches discovered system prompts while returning discovery metadata. npm/git package install/update, interactive trust prompting, trust.json persistence, full coding-session invocation wiring, and exact provider-facing prompt fixture text remain later phase work. |
| Compaction | `packages/coding-agent/src/core/compaction/*`, `packages/agent/src/harness/*` | Complete for Phase 7 | Java now has context usage/status, PI-style cut-point planning, summary prompt serialization, retained tail messages, manual compaction, threshold compaction, overflow recovery, pre-summarization tool-result pruning/tool-call argument truncation, and provider-backed branch summary hooks that persist `branchSummary` transcript messages. Runtime/session ownership still needs the Phase 8 PI-style conversation-context cleanup. |
| Parity gap fix phase | PI references listed above | Phase 8 next | Close known cross-module gaps before SDK/CLI work. Provider parity is first: provider-specific options, timeout/retry ownership, feature flags, endpoint/base-url resolution, and auth-mode readiness. Then close session-owned conversation context, stronger session validation/resume semantics, tool schema/result audit, custom message prompt audit, and resource/settings doc cleanup. |
| SDK/runtime API | `packages/coding-agent/src/core/agent-session*.ts`, `sdk.ts` | Phase 9, not started | Must mirror `AgentSession`, `AgentSessionRuntime`, services, new/resume/fork/clone/import responsibilities after Phase 8 closes the conversation ownership gap. |
| CLI modes | `packages/coding-agent/src/modes/*`, `cli/*` | Phase 10, not started | Must mirror print, JSON, RPC, session flags, model/tool flags, and event payloads. |
| Extension SPI | `packages/coding-agent/src/core/extensions/*` | Phase 11, not started | Java SPI should mirror PI hook names and timing first, then document Java-specific differences. |
| Interactive shell/TUI | `packages/coding-agent/src/modes/interactive/*` | Phase 12, not started | Mirror interaction model, command names, selectors, queue controls, and status semantics before Java terminal rendering choices. |

## Intentional Divergence Log

| Area | Divergence | Reason | Follow-up |
| --- | --- | --- | --- |
| Language/runtime | Java 21 modules instead of TypeScript packages | Project goal is a Java port. | Keep module responsibilities mapped to PI packages. |
| Extension execution | Java SPI first, no native TypeScript extension execution initially | Security and runtime complexity. | Revisit in Phase 13 PI Package Bridge. |
| Tool binary guard | Removed NUL-byte binary rejection from read/edit/grep | PI read decodes non-image files as UTF-8 and does not use this binary rejection. | Add image handling or charset policy only when matching PI behavior. |
| Current AI/core bridge | `agent4j-core.AgentMessage` still stores JSON content while `agent4j-ai` has typed content | Intentional current-phase design to preserve PI session JSONL round-tripping. | Keep using typed transcript views plus `AgentMessageConverter` as the `convertToLlm` boundary. Revisit promotion only after broader PI fixture coverage. |
| Agent loop message ownership | Java keeps `AgentConversationContext` inside the raw loop while PI mutates `AgentState.contextMutable()` | The Java raw loop is still persistence-agnostic until SDK/runtime APIs exist, but the internal shape is now closer to PI: one context owns the working transcript and generated messages, model input is temporary boundary data, and assistant output is derived for result reporting. | Phase 9 SDK/runtime should attach this context to the session runtime instead of reintroducing loop-local canonical state. |

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
