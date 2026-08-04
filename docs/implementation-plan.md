# agent4j PI Port Implementation Plan

This plan turns the PI compatibility contract into executable milestones. Each
phase should leave the repo in a buildable state with tests that lock down the
new behavior.

## Guiding Rules

- Preserve PI-compatible external artifacts and stay as close as practical to
  PI's internal implementation model. Internal parity is a design constraint,
  not an optional cleanup pass.
- Before marking a subsystem complete, compare it against the corresponding PI
  package and document any intentional divergence with a reason.
- Prefer PI naming, lifecycle boundaries, event sequencing, message shapes, and
  conversion points unless Java type safety or platform constraints require a
  different shape.
- Keep each module independently understandable.
- Prefer fake providers and deterministic operation interfaces in tests.
- Add provider SDKs, terminal UI, and extension bridges only after the core
  harness behavior is stable.
- Treat session JSONL compatibility as the backbone of the port.
- Pin module responsibilities to PI's package split:
  - `agent4j-ai` mirrors PI `@earendil-works/pi-ai`: provider-neutral LLM
    message/content types, model references, stream contracts, provider
    adapters, usage, and request options.
  - `agent4j-core` mirrors PI `@earendil-works/pi-agent-core`: agent loop,
    eventing, queue semantics, tool orchestration, and conversion boundaries
    from harness/custom messages to LLM messages.
  - `agent4j-coding` mirrors PI `@earendil-works/pi-coding-agent`: coding
    tools, session JSONL persistence, resource discovery, and coding-agent
    custom message conversions.
- Keep provider-neutral LLM content blocks in `agent4j-ai`; do not use raw
  `JsonNode` as the long-term AI message contract except for unknown/provider
  extension payloads.
- Treat `agent4j-core` messages as the agent transcript: PI-compatible LLM
  messages plus custom/session-only messages. Convert them to `agent4j-ai`
  `AiMessage` values at the model boundary, like PI's `convertToLlm`.
- Migrate the transcript model toward PI's `AgentMessage = Message |
  CustomAgentMessages[...]` union, while preserving PI session JSONL round-trip
  compatibility through explicit envelope/typed-view boundaries.

## Phase 1: Session Foundation

Status: in progress

Goal: read, validate, write, and navigate PI-shaped session JSONL.

Tasks:

- Complete typed Java models for all PI session entry types. Started.
- Complete typed Java models for all PI message roles. Done.
- Keep unknown-field preservation for forward compatibility. Done.
- Add typed payload views for known entry types. Started.
- Add append-only session writer with file locking. Done.
- Add typed append helpers for common entry types. Done.
- Add session cursor management for active branch tracking. Done.
- Add fork, clone, import, and in-place tree navigation primitives. Started.
- Add golden fixtures under `agent4j-testkit`. Started.

Exit criteria:

- `agent4j-coding` can load existing PI session fixtures.
- Writing and reading a session preserves all compatibility-critical fields.
- Branch navigation tests cover sibling branches and retained history.
- Session storage, tree navigation, and resume/fork helpers have been audited
  against PI `SessionManager`/session-tree behavior, with any Java-specific
  divergence documented.

## Phase 2: Core Message And Event Model

Status: in progress

Goal: define the agent transcript and event surface used by CLI, RPC, tests,
and future UI. The transcript should follow PI's `AgentMessage = Message |
CustomAgentMessages[...]` pattern, while LLM-native content block types live in
`agent4j-ai`.

Tasks:

- Add `agent4j-core` message model. Started.
- Audit and align message roles, event names, and event order with PI
  `@earendil-works/pi-agent-core` before extending the model further.
- Add agent event types for:
  - message start/update/end
  - tool execution start/update/end
  - queue update
  - agent start/end
  - retry start/end
  - compaction start/end
- Add event bus and subscription lifecycle. Done.
- Add abort controller abstraction. Done.
- Add usage and cost accumulator types. Done.
- Add fake text-turn runtime for event contract tests. Done.
- Add typed content blocks for assistant text, reasoning, and tool calls. Done,
  but migrate these to the PI-aligned `agent4j-ai` content model or bridge them
  explicitly before Phase 4 is considered stable.
- Add a PI-style custom message extension model for session-only messages such
  as bash execution, branch summary, compaction summary, and custom extension
  messages. Started in `agent4j-coding` with typed wrappers and LLM conversion
  text for coding-agent session-only roles.
- Add an explicit `convertToLlm` boundary from agent transcript messages to
  `agent4j-ai` messages. Done for the core boundary and default standard-role
  converter; started for coding-specific custom/session converters.

### AgentMessage Migration Plan

Status: complete for this migration pass; retain the envelope as the durable
model and use typed views as the internal PI-compatible bridge.

Target shape:

- `AgentMessage` remains the durable transcript envelope:
  `id`, `parentId`, `timestamp`, role discriminator, content, metadata, and
  unknown/session fields needed for JSONL compatibility.
- Use PI-style typed transcript views over the envelope instead of raw role
  switches at call sites:
  - standard message views: user, assistant, tool result
  - custom/session views: bash execution, branch summary, compaction summary,
    custom extension message
  - unknown view for forward-compatible payloads
- Keep `agent4j-ai` as the provider-neutral LLM message/content model.
  `agent4j-core` transcript views convert to `agent4j-ai` only through
  `AgentMessageConverter`, matching PI's `convertToLlm` boundary.

Migration steps:

1. Add typed view API on top of the current envelope. Done with
   `AgentMessageView` and user/assistant/tool-result/custom/unknown view
   records. These views preserve access to the original envelope for
   unknown-field round-tripping.
2. Move role-specific parsing helpers from scattered code into the view layer.
   Done for assistant tool-call extraction, tool-result metadata and envelope
   creation, custom/session payload extraction, and prompt-message inference.
   New role-specific parsing should continue to live in views instead of raw
   role switches at call sites.
3. Update `DefaultAgentMessageConverter` and `CodingAgentMessageConverter` to
   consume typed views instead of switching directly on raw `AgentMessageRole`.
   Done: both converters enter through `AgentMessage.view()` and only delegate
   to the standard converter at the LLM boundary.
4. Update `AgentLoop` internals to use typed views for assistant tool-call
   extraction and tool-result message creation, while continuing to return and
   persist `AgentMessage` envelopes. Done: `AgentLoop` uses the assistant view
   for tool calls and the tool-result view factory for generated tool-result
   transcript envelopes.
5. Add parity tests. Done for this pass:
   - standard and custom message roles produce the expected typed view
   - future metadata fields survive tool-result envelope creation and session
     append/reload flows
   - active PI-shaped session message paths can rebuild `AgentMessage`
     envelopes for resume/replay without losing message metadata
   - custom/session-only messages are filtered or converted only at
     `convertToLlm`
   - standard user/assistant/tool-result messages still convert at the default
     LLM boundary
   - tool-call and tool-result ordering remains covered by AgentLoop tests
6. Decide whether to promote the view hierarchy into the primary Java
   transcript model. Decision: do not promote now. Keep `AgentMessage` as the
   durable JSONL-compatible envelope and use the view hierarchy as the internal
   typed bridge. Revisit only after PI fixture round-trip coverage proves that a
   promoted hierarchy can preserve compatibility-critical unknown fields.

Constraints:

- Do not move LLM-native content blocks out of `agent4j-ai`.
- Do not make SessionManager depend on provider-specific AI messages.
- Do not remove raw payload preservation until PI fixture round-trip tests prove
  no compatibility-critical fields are lost.

Exit criteria:

- Fake runtime can emit a complete assistant text turn.
- Events can be serialized for JSON/RPC mode without UI dependencies.
- Event payloads and ordering are close to PI's `agent_start`, `turn_start`,
  `message_start`, `message_update`, `message_end`, `tool_execution_*`,
  `turn_end`, and `agent_end` semantics, with documented differences.

## Phase 3: Tool Runtime

Status: complete for external behavior; internal parity audit pending

Goal: implement PI's built-in coding tools with deterministic tests.

Tasks:

- Audit tool definitions, argument names, descriptions, result content shapes,
  preview/update behavior, and execution ordering against PI coding-agent tools.
- Add `Tool`, `ToolSpec`, `ToolCall`, and `ToolResult` core abstractions. Done.
- Add tool registry and executor. Done.
- Add operation interfaces:
  - `FileSystemOps`. Done.
  - `ProcessOps`. Done.
  - `Clock`. Done through `ToolContext`.
  - optional `PathPolicy`. Done.
- Implement first parity tools:
  - `read`. Done.
  - `write`. Done.
  - `edit`. Done.
  - `bash`. Done.
- Implement second parity tools:
  - `ls`. Done.
  - `grep`. Done.
  - `find`. Done.
- Add truncation behavior and output-size limits. Done for first and second parity tools.
- Add edit diff generation. Done.
- Add repeated-match edit diagnostics and context snippets. Done.
- Add local process integration coverage. Done.
- Add binary file safeguards. Removed for PI parity; non-image files are decoded
  as UTF-8 for now.
- Document tool result JSON contract. Done.
- Add tests for path handling, missing files, binary files, large files,
  failed edits, command timeout, exit codes, and output truncation.

Exit criteria:

- Tools can run without a model. Done.
- Tool results have stable JSON shapes for session persistence and events. Done.
- Any differences from PI's tool implementation behavior are documented in
  `docs/tool-result-contract.md` or a dedicated parity note.

## Phase 4: Agent Loop

Status: complete for fake-runtime parity

Goal: run a complete tool-calling agent turn against a fake streaming model.

Tasks:

- Keep the loop structure close to PI `agentLoop` / `runAgentLoop`: maintain an
  agent transcript, transform it to LLM messages only at the model boundary,
  execute tool calls, append tool-result messages in assistant source order, and
  continue until PI-equivalent stop conditions are reached.
- Replace the initial raw-JSON `AiMessage` slice with PI-style `agent4j-ai`
  message/content types:
  - `AiTextContent`. Done.
  - `AiThinkingContent`. Done.
  - `AiImageContent`. Done.
  - `AiToolCallContent`. Done.
  - `AiUserMessage`. Done.
  - `AiAssistantMessage`. Done.
  - `AiToolResultMessage`. Done.
  - `AiMessage`. Done.
- Add `agent4j-ai` streaming interfaces. Done for Phase 4; streams PI-style
  assistant message lifecycle events for message start/done/error and
  text/thinking/tool-call start/delta/end fragments.
- Add fake model client in `agent4j-testkit`. Done.
- Implement agent turn loop:
  - build context. Done.
  - stream assistant deltas. Done.
  - collect tool calls. Done.
  - execute tools. Done.
  - append tool-result transcript messages. Done with source-ordered sequential
    and parallel fake model turns.
  - continue until terminal stop reason. Done.
- Match PI turn event ordering: `agent_start`, `turn_start`, assistant message
  stream/update events, tool execution events, tool-result message artifacts,
  `turn_end`, queue drain, and `agent_end`. Done for text-only, single-tool,
  multi-tool, queue, retry, abort, and terminate fake model turns.
- Match PI tool execution mode semantics, including default parallel execution
  where safe and ordered tool-result message emission. Done with configurable
  sequential/parallel execution, default parallel execution, and ordered
  multi-tool result emission coverage.
- Add PI-style tool execution update callbacks. Done with `ToolContext`
  update publishing and `tool_execution_update` event coverage.
- Add PI-style before/after tool execution hooks. Done with loop-level hooks
  that run inside the `tool_execution_start` / `tool_execution_end` event
  window, can publish tool updates, run after-hooks for normal and blocked
  results, and keep ordered transcript emission after `tool_execution_end`.
- Add PI-style blocked tool results. Done with hook-returned blocked
  `ToolResult` values that skip execution and preserve normal result emission.
- Add PI-style terminate hints. Done with `ToolResult` metadata that ends
  the loop after normal tool-result emission without another model request.
- Implement prompt, steer, and follow-up queue semantics. Done with
  prompt `newMessages`, steering drain after completed turns, follow-up drain
  when the loop would otherwise stop, and one-at-a-time/all queue modes.
- Implement retry policy for retryable provider errors. Done with bounded
  model-round retry events plus success and exhaustion coverage.
- Implement abort behavior across model stream and tool execution. Done with
  model-stream and tool-abort coverage; tool aborts now escape as agent aborts.
- Persist messages through `SessionManager`. Done for Phase 4 `AgentMessage`
  outputs via PI-shaped message entries and active-path parent chaining.
- Add event JSON/RPC serialization coverage. Done for queue, retry, tool
  execution update/end, turn end, agent end, and abort event payloads.

Exit criteria:

- `agent4j-ai` exposes typed PI-style LLM messages and content blocks rather
  than `JsonNode` content bags.
- `agent4j-core` converts custom/session transcript messages to LLM-compatible
  `agent4j-ai` messages at the model boundary through an injectable converter.
- Tests cover text-only turns, single-tool turns, multi-tool turns, tool errors,
  retries, aborts, steering, and follow-ups.
- Tests assert PI-compatible event ordering and tool-result message ordering.

## Phase 5: Settings And Resource Discovery

Status: complete for resource-loader boundary

Goal: reproduce PI's project/user configuration discovery.

Tasks:

- Mirror PI's resource-loader boundaries and precedence rules before adding
  Java-specific configuration conveniences. Done with `ResourceLoader`,
  `ResourceDiscoveryOptions`, and typed resource file records.
- Implement agent directory resolution. Started with global `~/.pi/agent` and
  current-project `.pi` directories.
- Implement global and project settings loading. Started with
  `~/.pi/agent/settings.json` and `.pi/settings.json`.
- Implement settings merge rules. Started with global-then-project precedence,
  nested object merge, array/scalar replacement, typed accessors, and
  unknown-field preservation.
- Implement context file loading:
  - global `AGENTS.md`. Done for `~/.pi/agent/AGENTS.md`.
  - parent directory `AGENTS.md`. Done by walking ancestors from cwd.
  - project/current directory `AGENTS.md`. Done for cwd.
  - `CLAUDE.md`. Done as fallback when `AGENTS.md` is absent in the same
    directory.
  - `SYSTEM.md`. Started with global/project replacement precedence.
  - `APPEND_SYSTEM.md`. Started with global then project append order.
- Implement prompt template loading. Started with global
  `~/.pi/agent/prompts/*.md`, project `.pi/prompts/*.md`, settings `prompts`
  paths relative to their settings file, direct-directory loading, simple glob
  includes/excludes, template-name dedupe, frontmatter `description` and
  `argument-hint`, and a prompt-template disable flag.
- Implement skill metadata loading and prompt formatting. Started with
  metadata loading for `.pi/skills`, `.agents/skills`, `~/.pi/agent/skills`,
  `~/.agents/skills`, and settings `skills` paths. Skill discovery parses
  required `name`/`description`, optional `license`, `compatibility`,
  `allowed-tools`, and `disable-model-invocation`, keeps first skill on name
  collisions, reports diagnostics, and supports a skill-discovery disable flag.
  Started system prompt formatting with a `SystemPromptBuilder` that assembles
  selected `SYSTEM.md`, ordered `APPEND_SYSTEM.md`, context files, and
  model-visible skill metadata while hiding `disable-model-invocation` skills.
  Started system prompt transport with `AiSystemMessage` and optional
  `AgentLoopRequest.systemPrompt`, so assembled prompts can be sent to the
  model without being persisted as transcript messages. Started coding-agent
  request preparation with `CodingAgentLoopRequestFactory`, which discovers
  resources, assembles the system prompt, preserves the original loop request
  fields, and returns discovery metadata for diagnostics. Full `AgentSession`
  wiring and fixture-based exact text parity are still pending.
- Implement theme discovery. Done for global `~/.pi/agent/themes/*.json`,
  project `.pi/themes/*.json`, settings `themes` paths, CLI-supplied theme
  paths, theme-name override behavior, and `--no-themes` style disabling.
- Implement package resource loading. Done for local package directories from
  settings `packages`, package `pi.prompts`/`pi.skills`/`pi.themes` manifest
  entries, conventional `prompts`/`skills`/`themes` directories, package
  resource filters, package disabling, and diagnostics for unsupported
  non-local package sources. npm/git install/update/reconcile behavior remains
  a later CLI/package-management responsibility.
- Implement project trust/resource gating. Done with explicit
  `ProjectTrustPolicy` enforcement: untrusted discovery skips protected
  project settings, `.pi` prompt/system/skill/theme resources, and project
  `.agents/skills`, while still loading context files. Interactive prompting
  and `trust.json` persistence remain later CLI/runtime responsibilities.

Exit criteria:

- Tests cover resource precedence and disabled context files. Started for
  context files, system/append prompt files, settings precedence, nested
  settings merge, malformed settings, prompt template discovery/metadata, and
  skill discovery/metadata/diagnostics. Done for the Phase 5 resource-loader
  boundary, including themes, package resources, unsupported package
  diagnostics, project trust gating, and resource disable flags.
- System prompt builder can reproduce the expected ordered inputs. Started with
  tests for selected system prompt, append order, context ordering/escaping, and
  hidden skills. `AgentLoop` tests now cover system-prompt transport into the
  model request, and coding runtime tests cover resource-backed request
  preparation.
- Discovery order and merge behavior have parity tests based on PI fixtures or
  documented PI behavior. Done against documented PI behavior for this phase.

## Phase 6: Provider Adapters

Status: complete

Goal: connect the agent loop to real LLM providers through `agent4j-ai`.

Tasks:

- Mirror PI `@earendil-works/pi-ai` provider abstraction first: model metadata,
  `Context`, `StreamOptions`, provider request hooks, timeout/retry options,
  usage accounting, and provider-normalized stream events. Done for the first
  abstraction slice with `AiProvider`, `AiProviderRequest`, `AiProviderContext`,
  `AiStreamOptions`, `AiProviderRequestHook`, PI-style model metadata, model
  references, API/input/thinking enums, cost metadata, compatibility metadata,
  and normalized streaming through existing `AiStreamEvent`.
- Implement model registry and model references. Done with `AiProviderRegistry`,
  `AiProviderSelection`, explicit/default `AiModelReference` resolution, and
  coding settings-backed provider/model selection.
- Implement auth storage abstraction. Done with `AiAuthStore`,
  `InMemoryAiAuthStore`, `EnvironmentAiAuthStore`, settings-backed auth
  resolution, and provider-backed `AgentLoop` auth injection.
- Add fake/recorded provider contract tests. Done with reusable
  `agent4j-testkit` fake provider, recorded provider fixture replay, normalized
  stream contract assertions, and a JSON fixture that covers text, tool calls,
  terminal message state, and usage.
- Add OpenAI adapter. Started with `OpenAiResponsesProvider`, Java `HttpClient`
  transport, injectable test transport, Responses API request serialization,
  SSE line parsing, text/function-call/usage normalization, auth/header/timeout
  handling, and fake-transport contract tests. Live tests remain optional and
  skipped until credentials are introduced.
- Add Anthropic adapter. Started with `AnthropicMessagesProvider`, Java
  `HttpClient` transport, injectable test transport, Messages API request
  serialization, SSE line parsing, text/tool-use/thinking/usage normalization,
  auth/header/timeout handling, and fake-transport contract tests. Live tests
  remain optional and skipped until credentials are introduced.
- Normalize streaming events into the core event model. Done for the current
  loop with both legacy `AiModelClient` and PI-style `AiProvider` entry points.
- Normalize tool calls and tool results. Done in `AgentLoop`: provider-normalized
  `AiToolCallContent` becomes core `ToolCall`, executed `ToolResult` envelopes
  are appended to the transcript, and follow-up provider requests receive
  `AiToolResultMessage`.
- Normalize usage reporting. Done by carrying provider `AiUsage` into per-turn
  `AgentEvent.TurnEnded` usage and aggregate `AgentLoopResult`/`AgentEnded`
  usage.
- Add timeout and retry configuration. Done with `AgentLoopRequest`
  `modelTimeout`, provider `AiStreamOptions.timeout`, loop-owned
  `maxModelRetries`, and coding settings defaults from `httpIdleTimeoutMs` and
  `retry.maxRetries`.

Exit criteria:

- Provider adapters pass contract tests with recorded/fake HTTP streams.
- Live tests are optional and skipped without credentials.
- Adapter contracts map to PI provider semantics closely enough that a PI stream
  fixture can be normalized into the same Java event sequence.

## Phase 7: Compaction

Goal: preserve PI-style context compaction and overflow recovery.

Tasks:

- Mirror PI compaction helper responsibilities: context serialization,
  cut-point selection, retained tail handling, branch summarization, and
  overflow retry shape.
- Add token estimation abstraction.
- Add context usage calculation.
- Add context status reporting. Done with `ContextStatus` and
  `CompactionService.status(...)`, which report model-aware usage, effective
  thresholds, remaining/context-window ratio, cutoff, and whether compaction
  would run without invoking the summarization model.
- Add manual compaction.
- Add threshold compaction.
- Add overflow-triggered compaction and retry. Done in `AgentLoop`: context
  overflow model errors bypass normal retry, force an overflow compaction when
  enabled, rebuild model input from the compacted transcript, and retry the
  same round.
- Add PI-style pre-summarization tool-result pruning and tool-call argument
  truncation. Done in `CompactionService` through a shared
  `CompactionMessagePreprocessor`: pruning is enabled by default with PI-shaped
  protect/minimum/max-output/excluded-tool defaults, while argument truncation is
  opt-in through `CompactionConfig.TruncateArgsConfig`.
- Persist compaction entries with summaries and retained tail messages.
- Add branch summary generation hooks. Done with `BranchSummaryService` in
  `agent4j-core` and `CodingBranchSummarizer` in `agent4j-coding`, which
  generate provider-backed `branchSummary` transcript messages for fork/resume
  workflows and persist them through `SessionManager`.

Exit criteria:

- Tests cover cut-point selection, retained tail persistence, manual compaction,
  threshold compaction, overflow retry, tool-result pruning, argument
  truncation, context status reporting, and branch summary generation.

## Phase 8: Parity Gap Fixes

Goal: close known module-level PI parity gaps before adding new SDK, CLI, or UI
surface area. This phase exists to avoid baking temporary internal divergences
into public APIs.

Tasks:

- Update `docs/adr/0002-pi-internal-parity.md` after each module gap is closed,
  including any remaining intentional divergence and its reason.
- First fix provider parity gaps in `agent4j-ai` before runtime/session work:
  - align provider-specific option models with PI where Java currently only has
    minimal OpenAI/Anthropic options. Done with the provider-options
    baseline: `AiGenerationOptions` now carries common max-output,
    temperature, top-p, top-k, tool-choice, parallel-tool-call, and metadata
    settings through `AiStreamOptions`; OpenAI and Anthropic adapters serialize
    the supported subset into request bodies with tests.
  - pin exact timeout and retry semantics at the provider abstraction boundary,
    including which layer owns idle timeout, total timeout, and retry
    classification. Done for the current provider path: coding settings
    `httpIdleTimeoutMs` becomes `AgentLoopRequest.modelTimeout`, which becomes
    provider transport request timeout; `AgentLoopRequest.maxModelRetries` owns
    model-round retries; provider calls made by `AgentLoop` carry
    `AiStreamOptions.maxRetries = 0` to avoid nested adapter retries; and
    `AiRetryClassifier` plus `AiProviderHttpException` classify transient HTTP
    and transport failures.
  - add provider/model feature flags and capability metadata needed by later
    runtime and CLI selection. Done with `AiProviderFeatures` and
    `AiModelFeatures`; OpenAI and Anthropic adapters expose provider flags and
    honor model/provider tool-calling, tool-choice, and parallel-tool-call
    support while preserving existing default behavior.
  - make endpoint/base-url resolution match PI's effective-model behavior and
    decide the fate of the local `agent4j-ai/README.md` parity note by
    committing it as module documentation or folding it into ADR/plan docs.
    Done with `AiEndpointResolver`: `AiProviderRequest` applies auth
    `baseUrl` to a request-scoped effective model, providers resolve endpoint
    URIs from `request.model().baseUrl()` plus their API path suffix. The
    duplicate `agent4j-ai` README parity note was removed, leaving this plan
    and ADR 0002 as the authoritative provider parity docs.
  - keep live ChatGPT/Codex subscription login flow in Phase 9, but make the
    provider abstraction ready for that auth mode in Phase 8. Done with
    `AiAuthMode`, access-token/subscription-ready `AiResolvedAuth` fields,
    OpenAI bearer-token handling, and settings parsing for `authMode`,
    `accessToken`, `expiresAt`, and auth metadata.
- Close the `AgentLoop` message-state parity gap by moving toward PI's
  session-owned conversation model: make the runtime/session context own the
  canonical mutable transcript, build provider-facing model input only at the
  model boundary, and reduce loop-local message collections to result/event
  accumulators where they remain necessary. Done for the raw loop with
  `AgentConversationContext`: it owns the working transcript and generated
  messages, while `AgentLoop` rebuilds provider-facing `AiMessage` input at each
  model boundary. Message-state reduction is complete for the raw loop:
  assistant-only results are derived from generated messages rather than tracked
  as a separate loop-owned message list. Phase 9 SDK/runtime should attach this
  context to a session runtime.
- Strengthen `SessionManager`/JSONL parity before SDK work:
  - validate parent references. Done: `SessionTree` rejects missing entry ids,
    duplicate ids, unknown/future `parentId` references, and self-parenting,
    and `SessionManager.open/importFrom` run that validation before resume or
    import succeeds.
  - validate malformed typed payloads. Done: `SessionDocumentValidator`
    validates required fields for known PI-shaped session/header/message/model/
    thinking/compaction/file/custom entries, preserves unknown fields, and is
    enforced by `SessionManager.open/importFrom`, stale snapshot checks, and
    append validation before any line is written.
  - define stale snapshot and multi-process same-file resume semantics. Done:
    repeated runs persist `AgentLoopResult.messages()` through
    `SessionManager.appendAgentLoopResult(...)`, resume rebuilds the next model
    transcript from `activeAgentMessages()`, and append attempts from a stale
    same-file snapshot fail with a reopen-required error while holding the
    append lock. Generated message batches are validated before writing and are
    appended under one freshness-checked file lock so a repeated-run result does
    not partially append or interleave at message granularity.
  - expand real PI fixture coverage for branches, compaction entries, branch
    summaries, unknown payloads, and malformed records. Done for Phase 8:
    testkit fixtures now cover branch/compaction/branch-summary resume,
    unknown forward-compatible entries/messages, and malformed typed payloads;
    session tests exercise codec validation plus `SessionManager.open/importFrom`.
- Audit `agent4j-coding` built-in tools against PI implementation details:
  argument schemas, descriptions, image/text read behavior, edit
  multi-replacement behavior, render/update events, and result content/details
  shape. Done for schema/result baseline: tool input schemas now expose only
  accepted arguments with descriptions and `additionalProperties: false`, and
  successful path-bearing results use workspace-relative paths. Remaining
  PI-source audit: exact description text, image read payload shape, edit
  multi-replacement behavior, and streaming render/update details.
- Audit coding custom/session message conversion against PI prompt text and
  variants for bash execution, branch summary, compaction summary, and custom
  extension messages. Done for Java prompt contract: coding `convertToLlm`
  renders bash execution, branch summary, compaction summary, and custom
  extension messages as pinned XML-like user-context wrappers, escapes custom
  message type attributes, and skips unknown custom roles until a PI source audit
  identifies concrete variants. Remaining PI-source audit is exact prompt text
  comparison against `packages/coding-agent/src/core/messages.ts`.
- Bring resource/settings parity docs up to date and pin remaining later-phase
  gaps: npm/git package install/update/reconcile behavior, interactive trust
  prompting, `trust.json` persistence, and exact provider-facing prompt fixture
  text. Done in `docs/resource-settings-parity.md`.
- Bring `agent4j-ai` parity docs up to date as provider slices complete:
  provider abstraction is implemented for Phase 6, Phase 8 owns provider
  options, timeout/retry semantics, feature flags, and endpoint/base-url
  parity, while Phase 9 owns live subscription login flow.
- Remove stale local module docs that duplicate the ADR/plan parity direction.
  Done: `agent4j-ai/README.md` was removed and provider parity status is kept
  in this plan plus ADR 0002.
- Close Phase 8 with a single audit note that records completed slices,
  verification coverage, and intentionally deferred PI parity work. Done in
  `docs/phase-8-closeout.md`.

Exit criteria:

- ADR 0002 and this plan accurately reflect module parity status after the
  scan.
- Tests or fixtures cover the closed parity gaps, especially provider options,
  timeout/retry semantics, feature flags, session ownership, parent validation,
  and session resume behavior.
- The next SDK/runtime phase can build on a PI-style session-owned conversation
  context rather than the current loop-local transcript/message-list shape.
- `docs/phase-8-closeout.md` summarizes closed gaps and deferred work before
  Phase 9 starts.

## Phase 9: SDK And Runtime API

Goal: expose a stable Java embedding API equivalent to PI's SDK concepts.

Tasks:

- Mirror PI `AgentSession`, `AgentSessionRuntime`, and harness service
  responsibilities before adding Java-only conveniences. Initial shape audit is
  done in `docs/sdk-runtime-shape-audit.md`: `agent4j-core` remains the generic
  loop/context layer, `agent4j-coding` owns the coding SDK/runtime API, and
  runtime/session code must own canonical conversation context.
- Add `AgentSession` as the user-facing persisted conversation handle in
  `agent4j-coding`, backed by `SessionManager` plus
  `AgentConversationContext`. Interface baseline is done in
  `com.agent4j.coding.sdk.AgentSession`; concrete session implementation is in
  the next creation/resume/prompt slices.
- Add `AgentSessionRuntime` in `agent4j-coding` as the services/lifecycle owner
  for providers, tools, events, resources, settings, sessions, compaction,
  branch summaries, and auth. Interface baseline is done in
  `com.agent4j.coding.sdk.AgentSessionRuntime`, with lifecycle methods for
  create/resume/import/clone/fork and SDK event subscription.
- Add request/response records for the runtime API instead of exposing long
  `AgentLoopRequest` constructors to SDK callers. Done for the first API
  surface: create, resume, import, clone, fork, prompt, prompt result, and
  session info records are in `com.agent4j.coding.sdk`.
- Add runtime replacement for new, resume, fork, clone, and import. These flows
  should initialize or refresh `AgentConversationContext` from
  `SessionManager.activeAgentMessages()` and persist generated loop messages
  through `SessionManager.appendAgentLoopResult(...)`.
- Add `AgentSession.prompt(...)`, which appends the user prompt, prepares
  resources/settings, runs `AgentLoop`, persists generated messages, refreshes
  session context, and returns SDK-facing prompt result metadata.
- Add SDK-facing event subscription backed by `AgentEventBus`, keeping
  `AgentEvent` as the Phase 9 listener payload and leaving CLI JSON/RPC event
  mapping to Phase 10.
- Add login/auth runtime API before CLI ownership:
  - provider-neutral `LoginService`/`AuthSession` API
  - ChatGPT/Codex subscription login flow, including browser OAuth and device
    code modes, so ChatGPT Plus/Pro/Team/Enterprise-style subscription access is
    a first-class runtime capability rather than a CLI-only concern
  - API-key login flow for OpenAI/Anthropic-compatible providers as the
    usage-based alternative to subscription login
  - access-token login flow for Codex/OpenAI-compatible automation and testing
  - local user credential store abstraction with explicit non-project-secret
    storage boundary
  - auth status/logout APIs that can expose provider auth mode and plan metadata
    when the provider makes it available
  - tests with fake OAuth server/transport and in-memory credential store; fake
    provider login is test infrastructure only, not the product target
- Add extension binding placeholders.
- Add API docs and examples.
- Re-check exact API names and lifecycle details against PI
  `agent-session`/`sdk` source before Phase 9 closeout, because those PI source
  files are not committed in this repository.

Exit criteria:

- Minimal Java example can create a session, subscribe to events, and prompt
  against a fake model.
- Runtime/session APIs own the canonical conversation context in the PI style,
  and tests show repeated runs can resume from that context without rebuilding
  history outside the runtime boundary.
- Login API supports real ChatGPT/Codex subscription login shape, including
  browser OAuth, device code, status, logout, persisted user credentials, and
  resolved auth for provider-backed runtime creation. Fake-provider auth covers
  the same contract in deterministic tests.

## Phase 10: CLI Modes

Goal: provide process entrypoints before investing in terminal UI.

Tasks:

- Mirror PI CLI mode semantics and JSON/RPC payloads before adding new flags.
- Add picocli argument parser.
- Implement `--mode json`.
- Implement `--print`.
- Implement `--mode rpc`.
- Add session flags:
  - continue
  - resume
  - no-session
  - session path/id
  - fork
  - name
- Add model and tool selection flags.
- Add `login`, `logout`, and auth-status commands as thin CLI wrappers over the
  Phase 9 login/auth API.

Exit criteria:

- CLI modes run against fake provider in tests.
- JSON mode emits stable JSONL events.

## Phase 11: Extension SPI

Goal: support harness customization without embedding TypeScript first.

Tasks:

- Mirror PI extension lifecycle names and hook timing as the default Java SPI.
  Phase 8 pins current tool-hook timing in `docs/tool-hook-timing-audit.md`;
  Phase 11 still owns exact extension hook names, discovery, and exception
  policy.
- Define Java extension interfaces.
- Add lifecycle hooks:
  - before agent start
  - context transform
  - tool registration
  - command registration
  - provider request/response hooks
  - session lifecycle hooks
- Add service-loader discovery.
- Add project trust placeholder for extensions that require local resources.

Exit criteria:

- A test extension can register a custom tool and mutate context.

## Phase 12: Interactive Shell And TUI

Goal: add human-facing interactive mode after the runtime is stable.

Tasks:

- Mirror PI interaction model, command names, selectors, and queue controls
  before choosing Java-specific terminal rendering details.
- Implement basic line-oriented interactive shell.
- Add slash commands.
- Add model selector.
- Add session selector.
- Add queue controls.
- Add terminal rendering library evaluation.
- Implement rich TUI only after CLI/RPC behavior is locked down.

Exit criteria:

- Basic interactive mode can run real sessions.
- Rich TUI has screenshot/manual QA coverage before being treated as parity.

## Phase 13: PI Package Bridge

Goal: decide whether PI package compatibility is worth the complexity.

Tasks:

- Inventory real PI packages we need to support.
- Decide between:
  - Java-only extensions
  - Node child-process bridge
  - RPC bridge
  - no package compatibility
- If bridging, sandbox execution and define trust prompts first.

Exit criteria:

- Explicit ADR documents the compatibility level and security model.

## Current Next Actions

1. Begin Phase 9 SDK/runtime API, starting with the PI-style `AgentSession` and
   `AgentSessionRuntime` shape.
2. Keep expanding fixtures with real PI session samples as they become
   available.
