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
  request preparation with `CodingAgentLoopRequestPreparer`, which discovers
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

- Mirror PI `AgentSession`, `CodingAgentRuntime`, and harness service
  responsibilities before adding Java-only conveniences. Initial shape audit is
  done in `docs/sdk-runtime-shape-audit.md`: `agent4j-core` remains the generic
  loop/context layer, `agent4j-coding` owns the coding SDK/runtime API, and
  runtime/session code must own canonical conversation context.
- Add `AgentSession` as the user-facing persisted conversation handle in
  `agent4j-coding`, backed by `SessionManager` plus
  `AgentConversationContext`. Interface baseline is done in
  `com.agent4j.coding.sdk.AgentSession`; concrete session implementation is in
  the next creation/resume/prompt slices.
- Add `CodingAgentRuntime` in `agent4j-coding` as the services/lifecycle owner
  for providers, tools, events, resources, settings, sessions, compaction,
  branch summaries, and auth. Interface baseline is done in
  `com.agent4j.coding.sdk.CodingAgentRuntime`, with lifecycle methods for
  create/resume/import/clone/fork and SDK event subscription.
- Add request/response records for the runtime API instead of exposing long
  `AgentLoopRequest` constructors to SDK callers. Done for the first API
  surface: create, resume, import, clone, fork, prompt, prompt result, and
  session info records are in `com.agent4j.coding.sdk`.
- Add runtime replacement for new, resume, fork, clone, and import. These flows
  should initialize or refresh `AgentConversationContext` from
  `SessionManager.activeAgentMessages()` and persist generated loop messages
  through `SessionManager.appendAgentLoopResult(...)`. Session creation is done:
  `CodingAgentRuntime.createSession(...)` creates a PI-shaped JSONL
  session, appends optional session-info/model-change entries, initializes an
  empty session-owned `AgentConversationContext`, and returns a
  `CodingAgentSession` handle. Resume is done:
  `CodingAgentRuntime.resumeSession(...)` opens the JSONL session,
  optionally navigates to a requested active entry, initializes the
  session-owned context from `SessionManager.activeAgentMessages()`, and can
  continue prompting without caller-rebuilt history. Import/clone/fork are done:
  SDK runtime import validates and copies JSONL through `SessionManager`,
  clone copies the full source document, and fork writes only the selected
  active path with a derived header before returning a refreshed session handle.
- Add `AgentSession.prompt(...)`, which appends the user prompt, prepares
  resources/settings, runs `AgentLoop`, persists generated messages, refreshes
  session context, and returns SDK-facing prompt result metadata. Prompt
  baseline is done for direct model-client runtimes:
  `CodingAgentSession.prompt(...)` creates an in-memory user prompt message,
  runs `AgentLoop` with session-owned active history, persists the full
  `AgentLoopResult.messages()` batch through `SessionManager`, and refreshes the
  session context from persisted active messages. Resource/settings preparation
  is deferred to the runtime builder slice so the SDK does not guess
  user home or trust state.
- Add SDK-facing event subscription backed by `AgentEventBus`, keeping
  `AgentEvent` as the Phase 9 listener payload and leaving CLI JSON/RPC event
  mapping to Phase 10. Done for the SDK baseline: `CodingAgentRuntime`
  exposes runtime-wide `subscribe(...)` plus session-filtered
  `subscribeSession(...)`, and tests pin prompt event delivery, event ordering,
  subscription close behavior, and session-id filtering through real
  `AgentSession.prompt(...)` calls.
- Add runtime service configuration. Done with `CodingAgentRuntime.Builder`,
  which configures `CodingAgentRuntime`'s event bus, optional direct
  `AiModelClient`, provider registry, tools, message converter, clock,
  compaction, branch summaries, and login service. Sessions depend only on
  their `CodingAgentRuntime`, and tests pin default configuration plus custom
  clock/event-bus usage through real prompt calls.
- Add login/auth runtime API before CLI ownership:
  - provider-neutral `LoginService`/`AuthSession` API. Baseline is done with
    `LoginService`, `AuthSession`, `AuthStatus`, `AuthCredentialStore`,
    `InMemoryAuthCredentialStore`, and `DefaultLoginService`, exposed through
    `CodingAgentRuntime.loginService()` and `CodingAgentRuntime.Builder`.
  - ChatGPT/Codex subscription login flow, including browser OAuth and device
    code modes, so ChatGPT Plus/Pro/Team/Enterprise-style subscription access is
    a first-class runtime capability rather than a CLI-only concern. API shape
    is done with browser/device-code start requests, `SubscriptionLoginStart`,
    `SubscriptionLoginCompletion`, `SubscriptionLoginClient`, and completion
    into `AiResolvedAuth.chatGptSubscription(...)`. OpenAI subscription login
    client mechanics are done with configurable OAuth endpoints, PKCE browser
    authorization-code exchange, device-code start, token polling, fake
    transport tests, and `DefaultLoginService.pollSubscriptionLogin(...)`
    persistence of completed credentials. Browser callback flow is done with
    `state -> flowId` mapping, service-level callback completion, completed
    credential persistence, and `BrowserSubscriptionLoginCallbackServer` for
    local callback hosting. Token refresh/status is done with
    `SubscriptionLoginClient.refreshLogin(...)`, OpenAI refresh-token grant
    support, `LoginService.refreshAuth(...)`, auto-refresh on
    `status(...)`/`resolveAuth(...)` for expired subscription sessions, and
    auth status metadata exposure. Production OAuth configuration is done with
    `OpenAiSubscriptionLoginClientOptions.codexDefaults()`, which defines the
    current Codex client ID, auth/token/device endpoints, localhost callback,
    connector scopes, ChatGPT Codex API base URL, and authorization
    parameters. `OpenAiCodingRuntimeOptions` enables that profile by default.
    `LoginService.loginOpenAiSubscription()` owns browser launching, loopback
    callback orchestration on the registered localhost port, credential
    persistence, cleanup, and the resulting `AuthStatus`. Callback lifecycle
    hardening is done for timeout, interruption/cancellation, OAuth error and
    duplicate callbacks, and server shutdown; all one-call exit paths remove
    temporary flow state and close the callback server. Live refresh validation and
    the exact production device-code protocol remain later auth work.
    Production token-response validation is done: access/refresh tokens must be
    nonblank, present token types must be bearer, expiry values must be valid,
    rotated refresh tokens replace prior values, and malformed browser/device
    payloads are rejected after temporary-flow cleanup.
    A separate `OpenAiSubscriptionLiveIT` is opt-in through
    `-Dagent4j.liveOpenAi=true` plus explicit environment variables for a
    private credential file and enabled model ID. It covers interactive browser
    login, persisted/reloaded credentials, refresh, resolved provider auth, and
    a real provider-backed prompt; it is excluded from normal CI discovery.
    SDK setup/login/device-code/status/logout/refresh/session usage is documented
    in `docs/openai-sdk-guide.md`, with a runnable
    `OpenAiSubscriptionSdkExample` that avoids printing credential secrets.
  - SDK convenience wiring for standard OpenAI runtime setup. Done with
    `OpenAiCodingRuntimeOptions` and `CodingAgentRuntime.builder().openAi(...)`,
    which assemble `OpenAiResponsesProvider`, `AiProviderRegistry`,
    `PersistentAuthCredentialStore`, and an optional
    `OpenAiSubscriptionLoginClient` from one SDK-facing options object.
    Deterministic tests cover provider-registry defaults, API-key login, and
    subscription login transport wiring.
  - API-key login flow for OpenAI/Anthropic-compatible providers as the
    usage-based alternative to subscription login. Done in `DefaultLoginService`
    with resolved `AiResolvedAuth.apiKey(...)` storage.
  - access-token login flow for Codex/OpenAI-compatible automation and testing.
    Done in `DefaultLoginService` with expiry/metadata-aware
    `AiResolvedAuth.accessToken(...)` storage.
  - local user credential store abstraction with explicit non-project-secret
    storage boundary. Done with `PersistentAuthCredentialStore`, a user-scoped
    JSON credential file at `~/.pi/agent/auth.json` by default, explicit
    `AuthSession` serialization, owner-only POSIX permissions where supported,
    default SDK runtime wiring, and tests that pin reload/delete behavior plus
    no project-file writes.
  - auth status/logout APIs that can expose provider auth mode and plan metadata
    when the provider makes it available. Baseline status/logout are done;
    subscription plan metadata will arrive with OAuth/subscription login.
  - tests with fake OAuth server/transport and in-memory credential store; fake
    provider login is test infrastructure only, not the product target. Done for
    the current API shape with a deterministic fake `SubscriptionLoginClient`
    plus OpenAI OAuth transport contract tests. Test hardening is done for
    callback completion through `LoginService`, expired browser state cleanup,
    timeout/cancellation cleanup, OAuth error/duplicate callback handling,
    callback-server shutdown,
    device-code pending/slow-down/expired/failure responses, refresh-token grant
    success/failure, persistent refresh-token roundtrip, and SDK convenience
    validation. The opt-in `OpenAiSubscriptionLiveIT` is implemented; its
    successful production execution remains the Phase 9 closure gate.
- Integrate resolved auth into provider-backed runtime creation. Done:
  `CodingAgentRuntime.Builder` can configure an `AiProviderRegistry`,
  `CodingAgentSession` selects either the configured direct `AiModelClient` or a
  provider/model from that registry, resolves provider auth
  through `LoginService.resolveAuth(...)`, and creates a provider-backed
  `AgentLoop` with request-scoped `AiResolvedAuth`. Tests pin API-key auth,
  ChatGPT subscription-token auth, prompt model override, and the missing
  client/registry failure path. Persistent credential storage is done; real
  OpenAI OAuth mechanics are implemented behind configurable endpoints; browser
  callback hosting, browser launching, refresh/status, and SDK convenience
  wiring are implemented. The opt-in live OAuth test is implemented; exact
  production device-code protocol parity remains later auth work.
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
  resolved auth for provider-backed runtime creation. The provider-backed
  runtime creation boundary and persisted user credential store are done with
  deterministic tests; OpenAI OAuth start/exchange/poll mechanics are covered by
  fake-transport tests, and callback hosting is covered where local socket
  binding is available. Token refresh/status is covered with fake-transport
  tests, SDK convenience wiring is covered with fake-transport tests, and
  auth edge cases are covered with deterministic failure-path tests. Browser
  launching and endpoint defaults are implemented; exact production device-code
  protocol parity remains later auth work.
- Before Phase 9 is marked closed, verify the configured production OAuth
  endpoints against the current OpenAI/Codex flow and run
  `OpenAiSubscriptionLiveIT` successfully with a real ChatGPT subscription.
  Neither has been performed in this repository environment yet; deterministic
  tests and the opt-in live-test harness are not equivalent evidence.

### Phase 9 Closeout Status

Implementation slices 1 through 7 are complete. Phase 9 remains
**verification-pending** until both production endpoint verification and a
successful live browser-login, refresh, resolved-auth, and provider-backed
prompt run are recorded. Do not mark this phase or the subscription-login
parity gap closed based solely on fake-transport coverage.

#### Remaining Phase 9 Gaps

1. Verify the `codexDefaults()` authorization, token, device-code, callback,
   and model API endpoint configuration against the current production
   OpenAI/Codex flow.
2. Execute `OpenAiSubscriptionLiveIT` successfully using a real ChatGPT
   subscription and retain the non-secret verification result.
3. Establish exact production device-code protocol parity. The current generic
   device-code client is covered by contract tests but must not be represented
   as verified ChatGPT/Codex production login until that protocol is confirmed.

## Phase 10: CLI Modes

Goal: provide process entrypoints before investing in terminal UI.

Implementation slices:

1. **PI CLI Shape Audit**
   - Map PI command names, mode selection, argument precedence, stdin/stdout,
     exit codes, and JSON/RPC event envelopes to `agent4j-cli`.
   - Record intentional Java divergences before adding commands.
   - Done in `docs/pi-cli-shape-audit.md`, pinned to PI `0.82.1`. It records
     mode/flag precedence, process I/O and exit contracts, JSON/RPC boundaries,
     and the scoped Java divergences that later slices must close or preserve.
2. **CLI Bootstrap And Runtime Factory**
   - Add the Picocli root command and a test-injectable runtime factory.
   - Resolve settings, workspace, provider/model, tools, and credential store
     through the Phase 9 runtime services rather than duplicating loop setup.
   - Done for the bootstrap boundary in `agent4j-cli`: `Agent4jCli` and
     `Agent4jRootCommand` parse PI-shaped baseline options, while
     `DefaultCliRuntimeFactory` discovers global/project resources, resolves
     the configured OpenAI model, installs `CodingTools` in
     `CodingAgentRuntime.Builder`, and returns a `CodingAgentRuntime`.
     A command-line API key is held only by an in-memory runtime credential
     store; the default path uses Phase 9's persistent credential store. Print,
     JSON, and RPC execution remain the following slices.
3. **Print Mode**
   - Implement a non-interactive prompt command that writes assistant output to
     stdout and diagnostics to stderr.
   - Cover success, tool use, provider failures, and cancellation with a fake
     provider.
   - Done with `PrintModeRunner`: `agent4j -p <prompt>` creates an isolated
     temporary SDK session, runs through `CodingAgentRuntime`, writes the final
     assistant text to stdout, and writes failures/aborts to stderr with a
     nonzero exit code. Fake-model tests cover text, a tool-call round, provider
     failure, cancellation, temporary-session cleanup, and root-command wiring.
     Persistent session selection remains Slice 6 work.
4. **JSON Event Mode**
   - Implement JSONL output from `AgentEvent` with stable event envelopes,
     sequencing, and no human-oriented stdout noise.
   - Pin serialization and error/abort behavior with fixtures.
   - Done with `JsonEventModeRunner` and `JsonEventSerializer`: `--mode json`
     creates an isolated temporary SDK session, prints its persisted PI session
     header, then subscribes to that session's `AgentEvent` stream before
     prompting. The serializer explicitly maps public PI event and field names;
     it does not expose Java record names, timestamps, session IDs, or default
     Jackson polymorphism. Tests pin header-first ordering, stream deltas,
     provider-failure diagnostics, abort events, and temporary-session cleanup.
5. **RPC Mode**
   - Define the stdin request / stdout response-event protocol from PI's
     observable contract.
   - Support request correlation, malformed input handling, and orderly
     shutdown before adding interactive controls.
   - Done with `RpcModeRunner`: `--mode rpc` reads LF-delimited JSON objects,
     writes correlated `response` records and mapped runtime events on stdout,
     continues after malformed or unsupported input, and shuts down in order on
     `shutdown` or EOF. It supports `prompt`, `steer`, `follow_up`, `abort`,
     `new_session`, `get_state`, `get_messages`, `set_session_name`, and
     `shutdown`. Prompt acceptance is acknowledged before the asynchronous
     worker emits events. Because the current SDK exposes queues only on a
     `PromptRequest`, queued steering/follow-up messages are drained in PI
     precedence order after the active SDK prompt completes; live in-turn queue
     injection remains a Phase 10 parity gap.
6. **Session Lifecycle Flags**
   - Add new, continue, resume, no-session, explicit session path/ID, fork, and
     name behavior as thin mappings to `CodingAgentRuntime`.
   - Test persistence, active-path selection, and mutually exclusive flags.
   - Done with `CliSessionLifecycle`: `--session`, `--session-id`, `--continue`,
     noninteractive `--resume`, `--fork`, `--session-dir`, `--no-session`, and
     `--name` resolve to `CodingAgentRuntime` create/resume/fork calls. Normal
     CLI modes now persist sessions below PI's cwd-encoded `~/.pi/agent/sessions`
     location; only `--no-session` creates a cleaned-up temporary session.
     Tests pin persistent creation, explicit resume, most-recent continuation,
     forking, and conflicting flag validation.
7. **Model And Tool Selection**
   - Add provider/model selection, tool enable/disable selection, and argument
     validation using the established settings/resource boundaries.
   - Done for the current OpenAI bootstrap. `--provider`/`--model` continue to
     override resource settings through `CliRuntimeRequest` and reject provider
     conflicts or bootstrap-unsupported providers. `--tools`,
     `--exclude-tools`, `--no-tools`, and `--no-builtin-tools` are parsed into a
     typed selection and filtered against the runtime-owned registry before
     `CodingAgentRuntime` is built. Unknown tools and conflicting
     include/disable flags fail clearly; filtering preserves registered tool
     order. All currently supplied CLI tools are built-ins, so
     `--no-builtin-tools` yields an empty registry until extension tools exist.
8. **Auth Commands**
   - Add `login`, `logout`, auth status, and refresh as thin wrappers over
     `LoginService`, including browser-login invocation without token output.
   - Keep production endpoint verification and live-login evidence owned by
     the Phase 9 gaps above.
   - Done with Picocli subcommands: `login`, `logout`, `auth-status`, and
     `refresh` delegate to `CodingAgentRuntime.loginService()`. `login`
     invokes the one-call OpenAI browser subscription flow. Status output is
     restricted to provider, authentication mode/state, and expiry: it never
     prints `AuthStatus.metadata()`, access tokens, API keys, or refresh
     tokens. Auth-only bootstrap supplies an internal OpenAI model reference
     when no model is configured, solely to assemble existing runtime services;
     it never issues a model request. Production endpoint verification and the
     opt-in live browser-login evidence remain Phase 9 closure work.
9. **CLI Contract Closeout**
   - Run fake-provider end-to-end coverage for print, JSON, RPC, session,
     selection, and auth commands.
   - Re-audit PI command names, event payloads, output streams, and exit codes;
     update the ADR before the next phase begins.
   - Done. `agent4j-cli` now has fake-provider contract coverage across print,
     JSON, RPC, session lifecycle, model/tool selection, and sanitized auth
     commands. The closeout re-audit confirms PI-shaped command names, JSONL
     events, stdout/stderr separation, and nonzero failure behavior for the
     implemented subset. Session conflicts are rejected before runtime
     construction, matching PI bootstrap ordering. Remaining differences are
     explicitly retained in `docs/pi-cli-shape-audit.md` and ADR 0002 rather
     than represented as complete parity.

Exit criteria:

- CLI modes run against fake provider in tests.
- JSON mode emits stable JSONL events.
- Print and RPC modes have pinned stdout/stderr and exit-code contracts.
- CLI commands delegate to the Phase 9 SDK/runtime rather than constructing
  independent sessions, loops, or auth state.

### Phase 10 Closeout Status

Phase 10 implementation is complete for its scoped print, JSON, RPC, session,
selection, and auth command surface. The current CLI is not yet full PI CLI
parity: live RPC queue mutation, automatic non-TTY mode selection, interactive
resume/global session selection, multi-provider construction, package and
extension commands, and the broader RPC protocol remain tracked divergences.
Phase 9 production OAuth verification remains separately pending.

## Phase 11: Interactive Shell And TUI

Goal: add human-facing interactive mode after the runtime is stable.

Tasks:

- Mirror PI interaction model, command names, selectors, and queue controls
  before choosing Java-specific terminal rendering details.
- Phase 11 Slice 1 is done in `docs/pi-interactive-shape-audit.md`, pinned to
  PI `0.82.1`. It records the runtime/session ownership boundary, startup and
  mode-selection contract, prompt/queue/cancellation semantics, renderer
  progression, selector/command order, and explicitly chooses JLine 3 for the
  basic line shell while deferring a rich TUI renderer decision. It also records
  live `AgentSession` queue mutation as a prerequisite for PI-compatible
  steering/follow-up controls.
- Phase 11 Slice 2 is done with `InteractiveModeRunner` and
  `InteractiveTerminal` in `agent4j-cli`. Default text mode now uses the same
  runtime factory and `CliSessionLifecycle` as the Phase 10 process modes,
  opens the resolved SDK `AgentSession`, and hands it to an injectable session
  runner. The temporary bootstrap runner reports session readiness only; Slice 3
  replaces it with the persistent line-oriented REPL.
- Phase 11 Slice 3 is done: `LineInteractiveSessionRunner` is the
  injected, persistent line-loop implementation. It submits each nonblank
  initial or entered line through the opened `AgentSession`, prints only final
  assistant text, reports a failed prompt to stderr without losing the session,
  ignores empty lines, and exits cleanly on EOF. This first loop uses buffered
  injected I/O; JLine editor integration follows after the loop contract is
  covered by tests. Fake-provider tests pin repeated prompts on one session,
  blank input, failure recovery, final text, and EOF shutdown.
- Phase 11 Slice 4 is done with `TerminalEventRenderer`. The interactive
  runner subscribes to the opened session before entering the host loop and
  closes that subscription during shutdown. Assistant `text_delta` content is
  written as it arrives; `message_end` is only a fallback for non-streaming
  providers, so final text is not duplicated. Tool start/update/end, retries,
  compaction, stream errors, and agent aborts render as concise terminal status
  or error rows. Renderer tests pin both streams and fallback/deduplication.
- Phase 11 Slice 5 is done. `LiveAgentQueues` now crosses the active
  `AgentSession`/`AgentLoop` boundary, and `AgentSession` exposes
  `isStreaming()`, `steer(...)`, `followUp(...)`, and `abort(...)`. The line
  shell keeps reading while a prompt task runs: ordinary entered text steers,
  `/follow-up <text>` queues follow-up work, and `/abort` cancels the active
  run. `InteractiveInterruptHandler` maps terminal SIGINT/Ctrl-C to the active
  session abort and restores the previous process handler when the shell exits.
  Buffered line I/O cannot distinguish Alt+Enter; `/follow-up` is the interim
  follow-up control until JLine key handling is introduced. SDK tests pin live
  steer/follow-up consumption in the same loop invocation and active-run abort.
- Phase 11 Slice 6 is done with `InteractiveCommandRegistry` and a command
  handler boundary intended for Phase 13 registrations. The line shell now
  supports `/help`, `/exit`, `/abort`, `/clear`, `/status`, `/name <name>`,
  `/compact`, `/new`, `/continue`, and `/resume <path|id>`. An
  `InteractiveSessionController` owns active-session replacement and event
  subscription rebinding, so lifecycle commands remain SDK/lifecycle calls.
  Manual compaction is exposed through `AgentSession.compact(...)` and the
  runtime compactor, rather than duplicating provider/compaction internals in
  the CLI. Phase 11 Slice 7 is done with the interactive local/global session
  picker, cross-project confirmation/forking, and model/provider selection.
- Phase 11 Slice 8 is done with a JLine 3-backed terminal rendering layer.
  ANSI-aware terminals receive styled markdown headings/code, tool activity,
  progress/status, and errors; pipes, tests, and `NO_COLOR`/dumb terminals use
  the existing plain line renderer.
- Phase 11 Slice 9 is done with fake-provider terminal contract coverage in
  `InteractiveContractTest`, plus the lower-level live-session queue and abort
  contracts. The closeout is recorded in `docs/phase-11-closeout.md`; the
  remaining editor, rich-TUI, extension, filename, and production-provider
  gaps are explicit and are not being counted as interactive parity.
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

## Phase 12: Live OpenAI Feature Walkthroughs

Goal: provide a progressive, real-provider learning path that demonstrates
agent4j's public feature composition against the OpenAI API.

Principles:

- Add an `agent4j-examples` Maven module containing runnable Java applications
  and matching Markdown walkthroughs under `docs/examples/`.
- Examples use the production `OpenAiResponsesProvider`, `CodingAgentRuntime`,
  and `CodingAgentSession` boundaries. They must not substitute
  fake models or fake providers for the feature being demonstrated.
- Require `OPENAI_API_KEY` and `AGENT4J_OPENAI_MODEL` from the environment;
  never accept keys as command-line arguments, print them, persist them in
  example sessions, or add them to source control.
- Make all live runs opt-in and exclude them from normal `mvn test` execution.
  CI compiles the examples; deterministic fake-provider tests remain the
  regression safety net.
- Bound each live invocation with explicit model, output-token, tool-round, and
  workspace limits. Each walkthrough records usage, elapsed time, model ID,
  request ID when available, cleanup instructions, and expected observable
  behavior without asserting exact model prose.
- Each successive example reuses the runtime/session setup established by the
  prior example rather than reimplementing agent construction.

Tasks:

1. Establish the live-example foundation. Done with `agent4j-examples`, shared
   `LiveExampleConfiguration`, and `LiveExamplePreflight`.
   - `LiveExampleConfiguration` validates required environment variables without
     displaying or persisting API-key values, bounds future example output/tool
     limits, and creates temporary workspace/session directories unless the user
     explicitly supplies paths. `CodingAgentRuntime` configures the production
     OpenAI runtime from those values.
   - The opt-in `live-openai-examples` Maven profile executes the selected
     example entry point; the current preflight entry point validates setup and
     cleanup without sending an API request. `docs/examples/README.md` covers
     API-key configuration, model selection, cost estimation, and cleanup.
   - The foundation registers no filesystem-writing or process-executing tools.
     Future tool walkthroughs must constrain any side effects to the example
     workspace and document them before execution.
2. Add progressive real OpenAI walkthroughs. All twelve are complete.
   - `01-real-prompt` creates the standard OpenAI runtime, sends one prompt,
     prints streaming assistant text and provider usage.
   - `02-streaming-events` reuses the runtime/session setup and renders public
     `AgentEvent` lifecycle boundaries.
   - `03-tool-calling` exposes only the no-side-effect `workspace_status` tool,
     then demonstrates model selection and execution. It fails clearly when a
     selected model does not support or invoke function calling.
   - `04-persistent-sessions` creates, prompts, releases, resumes, and inspects
     a JSONL session without callers rebuilding conversation history.
   - `05-live-session-control` demonstrates steering, follow-up, and
     cancellation during streamed real-provider runs, with terminal timing
     guidance for manual execution rather than exact-text assertions.
   - `06-resources-and-coding-tools` creates a disposable sample workspace,
     discovers its settings/resources, builds a request-scoped system prompt,
     and exposes only workspace-scoped read-only built-in tools.
   - `07-model-switching` changes an application's selected model between
     turns in one persisted session.
   - `08-prompt-model-override` uses the default then a per-prompt model
     override across consecutive turns in one persisted session.
   - `09-compaction-and-branching` manually compacts a session, persists its
     summary, forks a selected active path, resumes the compacted path, and
     documents summary-token cost and generated-session cleanup.
   - `10-cli-modes` invokes the actual CLI with the same environment-based
     credentials for print, JSON, RPC, and session resume/fork flows.
   - `11-interactive-shell` is complete: `InteractiveShellExample` starts the
     real interactive CLI with only the read-only `read` tool and an initial
     tool-use prompt. The example guide covers real streaming and tool activity,
     `/status`, `/model`, `/new`, `/resume`, `/follow-up`, `/abort`, and an
     ANSI/plain-terminal manual QA checklist.
   - `12-reference-application` is complete: `ReferenceApplicationExample`
     combines the public production runtime, a persisted session, streaming,
     discovered workspace resources, and a workspace-scoped read-only tool
     allowlist into the recommended safe onboarding application.
3. Validate and close out the examples.
   - Add compile-time/example-structure checks to normal CI.
   - Add environment-gated live verification commands that assert stable facts
     such as successful completion, event ordering, tool/session shape, and
     cleanup; do not assert exact natural-language output.
   - Record actual live execution evidence separately from deterministic unit
     tests, including the selected model and date but no secrets.
   - Reuse successful live OpenAI API examples as evidence toward the remaining
     Phase 9 production-provider verification gate; do not declare that gate
     closed until the documented OAuth and live subscription checks also pass.

Exit criteria:

- A user with a valid OpenAI API key can run the examples in order from a clean
  checkout and see each feature extend the prior one.
- Every feature introduced through Phase 11 has a real-provider walkthrough or
  an explicit documented exclusion.
- The reference application uses the same public runtime, session, and CLI
  boundaries recommended to users.
- Live examples are credential-safe, bounded, documented for cost and cleanup,
  and never run unintentionally in CI.

## Phase 13: Extension SPI

Goal: support harness customization without embedding TypeScript first.

Tasks:

- Mirror PI extension lifecycle names and hook timing as the default Java SPI.
  Phase 8 pins current tool-hook timing in `docs/tool-hook-timing-audit.md`;
  Phase 13 still owns exact extension hook names, discovery, and exception
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
- Add extension scope and project-trust gating. Done: `ExtensionScope` marks
  application versus project-scoped Java extensions; untrusted projects do not
  activate project-scoped extensions. `requiresProjectTrust()` remains a
  placeholder for later resource/package policy.

Exit criteria:

- A test extension can register a custom tool and mutate context.
- The Java SPI documents application/project scope, its trust boundary, and
  Phase 14 package-bridge non-goals.

Closeout: complete for the Java-only release. A ServiceLoader integration test
loads one application-classpath extension that contributes a safe tool, context
transform, lifecycle listener, and interactive command. Intentional gaps are
PI TypeScript/package compatibility, Node or other subprocess execution,
package installation/update/reconciliation, dynamic project-code loading,
project-local discovery, trust prompting/persistence, extension UI/renderers,
provider registration, session mutation, cancellation, and dynamic tool
activation. Phase 14 owns any package bridge plus its sandbox and trust model.

## Phase 14: PI Package Bridge

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

## Phase 15: Simple-Project Coding Agent

Goal: operate agent4j as a small, reliable coding agent for simple projects.
The agent must inspect a workspace, modify code, run verification, repair a
simple failure, and report the outcome. This phase is intentionally narrower
than full PI provider, package, and terminal parity; see
`docs/pi-mini-coding-agent-parity-audit.md`.

### Slice 1: Default coding-agent system prompt

- Add a versioned built-in coding-agent prompt that establishes workspace
  boundaries, inspect-before-edit behavior, tool use, verification, failure
  handling, and concise final reporting.
- Build selected-tool guidance from the active `ToolRegistry`.
- Define prompt composition and precedence for the built-in prompt, global or
  project `SYSTEM.md` replacement, `APPEND_SYSTEM.md`, `AGENTS.md` context,
  eligible skills, and explicit caller/CLI prompt overrides.
- Wire the resolved prompt into print, JSON, RPC, and interactive
  `PromptRequest` creation.
- Add OpenAI and Anthropic provider-request tests that assert the composed
  system prompt is sent and is not persisted in session JSONL.

Closeout: complete. `DefaultCodingSystemPrompt` provides the versioned
`agent4j-coding-v1` baseline. `SystemPromptBuilder` composes system-prompt
replacement, PI-style selected-tool listing and conditional guidelines, append
prompts, context files, and eligible skills. The CLI passes the resolved prompt through print, JSON, RPC,
and interactive requests; `--system-prompt` and repeatable
`--append-system-prompt` provide explicit overrides. Tests cover composition,
CLI construction, prompt requests, and OpenAI/Anthropic request serialization.

### Slice 2: Runtime resource and prompt integration

- Move resolved resource/prompt ownership behind a coding runtime preparation
  boundary rather than leaving it as a CLI-only discovery result.
- Ensure new, resumed, forked, and replaced sessions use the selected
  workspace's resources and existing project-trust policy.
- Add `--system-prompt` and `--append-system-prompt` with documented
  precedence and validation.
- Test project trust, resource precedence, session replacement, and all CLI
  execution modes.

### Slice 3: Tool reliability baseline

- Drain bash stdout and stderr concurrently while retaining timeout and abort
  behavior.
- Reject ambiguous edits before writing.
- Add bounded read offset/limit and line-number behavior for large files.
- Define default coding, read-only, and full tool profiles; default coding is
  `read`, `write`, `edit`, and `bash`.
- Make search behavior `.gitignore` aware where practical, and prevent
  symlink-based escapes from the workspace.
- Add deterministic tool tests for output pressure, edit safety, path safety,
  and profile selection.

### Slice 4: Deterministic mini-agent acceptance fixture

- Add a small fixture project containing one incomplete feature or intentional
  test failure.
- Drive the public CLI/runtime path with a scripted fake provider that inspects
  files, edits code, runs a test/build command, repairs the failure, and emits
  a final summary.
- Assert changed files, tool sequence, verification result, persisted session,
  and final assistant report.

### Slice 5: Real-provider smoke validation

- Reuse the acceptance fixture in credential-gated OpenAI and Anthropic smoke
  tests.
- Run inside an isolated temporary project, cap tool rounds/tokens, clean up
  all generated files, and never log credentials.
- Record provider/model, elapsed time, tool calls, changed files, and
  verification result as live evidence without asserting exact prose.
- Keep the tests out of normal CI.

### Slice 6: Practical provider expansion

- Add a configurable OpenAI-compatible provider adapter with base URL,
  headers, credentials, model capabilities, and model listing.
- Preserve native OpenAI and Anthropic behavior.
- Add further native providers according to user demand, beginning with the
  highest-leverage providers after the compatible adapter.
- Keep model catalog data separate from provider transports so catalog updates
  do not require adapter code changes.

### Slice 7: Essential CLI usability

- Add piped stdin and automatic non-TTY print behavior.
- Support text `@file` prompt inclusion; defer image attachments unless they
  become necessary for the acceptance fixture.
- Expose prompt templates and explicit model listing.
- Add basic interactive history, completion, and multiline input only where
  needed by normal coding-agent operation.

### Explicit deferrals

The following are not prerequisites for this phase:

- complete PI provider/model catalog parity;
- TypeScript extensions, PI packages, package installation, or dynamic
  project-code loading;
- rich TUI and full editor fidelity;
- extension widgets and project trust dialogs;
- a complete permission-policy engine, sandbox, subagents, or advanced
  orchestration.

Exit criteria:

- The deterministic fixture proves an agent can complete a bounded coding task
  through the public CLI/runtime path.
- At least one real OpenAI model and one real Anthropic model complete the
  opt-in smoke fixture within documented resource limits.
- The default prompt, project instructions, selected-tool guidance, and trust
  policy are observable in provider-request tests.
- Coding tools meet the documented reliability baseline.

## Current Next Actions

1. Start Phase 15 Slice 1: default coding-agent system prompt and CLI/runtime
   prompt wiring.
2. Keep Phase 9 production OAuth verification separate; record live-provider
   example evidence without treating it as sufficient OAuth closure evidence.
