# Phase 9 SDK Runtime Shape Audit

Phase 9 adds the Java SDK/runtime surface that callers should use instead of
constructing `SessionManager`, `AgentLoopRequest`, and `AgentLoop` directly for
normal coding-agent runs.

## PI Reference Shape

The parity target is PI's coding-agent SDK/runtime boundary:

- `packages/coding-agent/src/core/agent-session*.ts`
- `packages/coding-agent/src/core/sdk.ts`
- session-manager and harness session packages referenced by ADR 0002

The Java implementation should mirror the ownership model rather than copy
TypeScript syntax:

- `AgentSession` is the user-facing handle for one persisted conversation.
- `AgentSessionRuntime` owns services and lifecycle operations for new, resume,
  fork, clone, import, prompt, login/auth, and event subscription.
- The runtime owns canonical conversation context. Callers should not rebuild
  history manually between prompts.

## Current Java Shape

Current Java internals already have most lower-level pieces:

- `agent4j-core` owns the generic `AgentLoop`, `AgentLoopRequest`,
  `AgentLoopResult`, `AgentConversationContext`, events, tools, and compaction
  primitives.
- `agent4j-ai` owns provider registry, provider/model selection, resolved auth,
  stream options, provider features, and OpenAI/Anthropic adapters.
- `agent4j-coding` owns PI-shaped JSONL sessions through `SessionManager`,
  resource/settings discovery, system prompt assembly, coding message
  conversion, coding tools, session compaction, and branch summaries.

The gap is not missing low-level mechanics. The gap is a stable SDK boundary
that composes them in the PI style.

## Target Module Ownership

`agent4j-core` should stay persistence-agnostic:

- keep `AgentLoop` as the generic loop executor
- keep `AgentConversationContext` as the canonical mutable conversation object
- keep event, queue, retry, compaction, and tool abstractions generic

`agent4j-coding` should own the coding SDK/runtime:

- `com.agent4j.coding.sdk.AgentSession`
- `com.agent4j.coding.sdk.AgentSessionRuntime`
- session creation/resume/fork/clone/import flows backed by `SessionManager`
- prompt flows that build `AgentLoopRequest`, prepare resources/settings, run
  `AgentLoop`, persist `AgentLoopResult.messages()`, and refresh session context
- runtime service wiring for providers, auth, tools, resources, compaction,
  branch summaries, clocks, ids, and event bus

`agent4j-ai` should remain provider/auth infrastructure:

- provider registry and provider/model resolution
- resolved auth passed to provider-backed loop creation
- credential abstractions used by the runtime login service

## Target Java API Shape

Initial SDK classes should be small and explicit:

- `AgentSessionRuntime`
  - `createSession(CreateSessionRequest)`
  - `resumeSession(ResumeSessionRequest)`
  - `importSession(ImportSessionRequest)`
  - `cloneSession(CloneSessionRequest)`
  - `forkSession(ForkSessionRequest)`
  - `subscribe(Consumer<AgentEvent>)`
  - `loginService()`
- `AgentSession`
  - `id()`
  - `sessionFile()`
  - `cwd()`
  - `activeEntryId()`
  - `conversationContext()`
  - `prompt(PromptRequest)`
  - `compact(ManualCompactionRequest)` or equivalent wrapper
  - `fork(...)` and `cloneTo(...)` convenience methods that delegate to the
    runtime

Request/response records should be preferred over long overloaded constructors.
They should carry explicit Java types for cwd, session file, model selection,
tool execution mode, queue messages, abort signal, and per-run options.

## Conversation Ownership Contract

The runtime/session API must own conversation state:

- New sessions start with an empty `AgentConversationContext`.
- Resumed sessions initialize context from `SessionManager.activeAgentMessages()`.
- `prompt(...)` appends the user prompt to the session, runs the loop with the
  session-owned context transcript, persists generated messages, then refreshes
  the context from persisted active messages.
- Callers must not pass the full prior history on repeated prompts.

This keeps the public SDK aligned with PI's session-owned conversation model and
prevents `AgentLoopRequest.messages()` from becoming the public resume API.

## Event Contract

The runtime should expose subscription through the existing `AgentEventBus`.
For Phase 9, SDK listeners can receive the existing `AgentEvent` hierarchy.
Phase 10 can map the same events into CLI JSON/RPC payloads.

## Auth Contract

Login/auth belongs to the runtime layer before CLI commands are added:

- API-key login and access-token login can be implemented first.
- ChatGPT/Codex subscription login should expose browser OAuth and device-code
  shapes with deterministic fake OAuth tests.
- The credential store must be user-scoped, not project-scoped.
- Provider-backed session creation should resolve auth into `AiResolvedAuth`.

## Deferred Exact PI Audit

This audit uses the PI files named in ADR 0002 as the target shape, but the PI
source files are not checked into this repository. Before final Phase 9 closeout,
the Java API names and lifecycle details should be checked against the exact PI
`agent-session` and `sdk` source for the target PI version.
