# agent4j PI Port Implementation Plan

This plan turns the PI compatibility contract into executable milestones. Each
phase should leave the repo in a buildable state with tests that lock down the
new behavior.

## Guiding Rules

- Preserve PI-compatible external artifacts before matching PI internals.
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

## Phase 2: Core Message And Event Model

Status: in progress

Goal: define the agent transcript and event surface used by CLI, RPC, tests,
and future UI. The transcript should follow PI's `AgentMessage = Message |
CustomAgentMessages[...]` pattern, while LLM-native content block types live in
`agent4j-ai`.

Tasks:

- Add `agent4j-core` message model. Started.
- Add agent event types for:
  - message start/update/end
  - tool execution start/update/end
  - queue update
  - agent start/end/settled
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
  messages.
- Add an explicit `convertToLlm` boundary from agent transcript messages to
  `agent4j-ai` messages.

Exit criteria:

- Fake runtime can emit a complete assistant text turn.
- Events can be serialized for JSON/RPC mode without UI dependencies.

## Phase 3: Tool Runtime

Status: complete

Goal: implement PI's built-in coding tools with deterministic tests.

Tasks:

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

## Phase 4: Agent Loop

Status: started

Goal: run a complete tool-calling agent turn against a fake streaming model.

Tasks:

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
- Add `agent4j-ai` streaming interfaces. Started; revise them to stream
  PI-style assistant message events:
  - message start/done/error
  - text start/delta/end. Started with delta.
  - thinking start/delta/end. Started with delta.
  - tool-call start/delta/end. Started with delta.
- Add fake model client in `agent4j-testkit`. Started.
- Implement agent turn loop:
  - build context. Started.
  - stream assistant deltas. Started.
  - collect tool calls. Started.
  - execute tools. Started.
  - append tool results. Started.
  - continue until terminal stop reason. Started.
- Implement prompt, steer, and follow-up queue semantics.
- Implement retry policy for retryable provider errors.
- Implement abort behavior across model stream and tool execution.
- Persist messages through `SessionManager`.

Exit criteria:

- `agent4j-ai` exposes typed PI-style LLM messages and content blocks rather
  than `JsonNode` content bags.
- `agent4j-core` converts custom/session transcript messages to LLM-compatible
  `agent4j-ai` messages at the model boundary.
- Tests cover text-only turns, single-tool turns, multi-tool turns, tool errors,
  retries, aborts, steering, and follow-ups.

## Phase 5: Settings And Resource Discovery

Goal: reproduce PI's project/user configuration discovery.

Tasks:

- Implement agent directory resolution.
- Implement global and project settings loading.
- Implement settings merge rules.
- Implement context file loading:
  - global `AGENTS.md`
  - parent directory `AGENTS.md`
  - project `AGENTS.md`
  - `CLAUDE.md`
  - `SYSTEM.md`
  - `APPEND_SYSTEM.md`
- Implement prompt template loading.
- Implement skill metadata loading and prompt formatting.
- Stub theme discovery for later UI use.

Exit criteria:

- Tests cover resource precedence and disabled context files.
- System prompt builder can reproduce the expected ordered inputs.

## Phase 6: Provider Adapters

Goal: connect the agent loop to real LLM providers through `agent4j-ai`.

Tasks:

- Implement model registry and model references.
- Implement auth storage abstraction.
- Add OpenAI adapter.
- Add Anthropic adapter.
- Normalize streaming events into the core event model.
- Normalize tool calls and tool results.
- Normalize usage reporting.
- Add timeout and retry configuration.

Exit criteria:

- Provider adapters pass contract tests with recorded/fake HTTP streams.
- Live tests are optional and skipped without credentials.

## Phase 7: Compaction

Goal: preserve PI-style context compaction and overflow recovery.

Tasks:

- Add token estimation abstraction.
- Add context usage calculation.
- Add manual compaction.
- Add threshold compaction.
- Add overflow-triggered compaction and retry.
- Persist compaction entries with summaries and retained tail messages.
- Add branch summary generation hooks.

Exit criteria:

- Tests cover cut-point selection, retained tail persistence, manual compaction,
  threshold compaction, and overflow retry.

## Phase 8: SDK And Runtime API

Goal: expose a stable Java embedding API equivalent to PI's SDK concepts.

Tasks:

- Add `AgentSession`.
- Add `AgentSessionRuntime`.
- Add runtime replacement for new, resume, fork, clone, and import.
- Add extension binding placeholders.
- Add API docs and examples.

Exit criteria:

- Minimal Java example can create a session, subscribe to events, and prompt
  against a fake model.

## Phase 9: CLI Modes

Goal: provide process entrypoints before investing in terminal UI.

Tasks:

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

Exit criteria:

- CLI modes run against fake provider in tests.
- JSON mode emits stable JSONL events.

## Phase 10: Extension SPI

Goal: support harness customization without embedding TypeScript first.

Tasks:

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

## Phase 11: Interactive Shell And TUI

Goal: add human-facing interactive mode after the runtime is stable.

Tasks:

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

## Phase 12: PI Package Bridge

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

1. Decide and document exact resume semantics for multi-process same-file
   sessions, including stale snapshot handling.
2. Add stronger validation for parent references and malformed typed payloads.
3. Add remaining session helpers for compaction entries once compaction shape is
   finalized.
4. Expand Phase 4 beyond the initial fake streaming/tool-call loop: queue
   semantics, retry policy, abort coverage, and `SessionManager` persistence.
5. Keep expanding fixtures with real PI session samples as they become
   available.
