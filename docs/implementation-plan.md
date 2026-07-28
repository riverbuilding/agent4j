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

Goal: define the provider-neutral runtime surface used by CLI, RPC, tests, and
future UI.

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

Exit criteria:

- Fake runtime can emit a complete assistant text turn.
- Events can be serialized for JSON/RPC mode without UI dependencies.

## Phase 3: Tool Runtime

Goal: implement PI's built-in coding tools with deterministic tests.

Tasks:

- Add `Tool`, `ToolSpec`, `ToolCall`, and `ToolResult` core abstractions.
- Add operation interfaces:
  - `FileSystemOps`
  - `ProcessOps`
  - `Clock`
  - optional `PathPolicy`
- Implement first parity tools:
  - `read`
  - `write`
  - `edit`
  - `bash`
- Implement second parity tools:
  - `ls`
  - `grep`
  - `find`
- Add truncation behavior and output-size limits.
- Add edit diff generation.
- Add tests for path handling, missing files, binary files, large files,
  failed edits, command timeout, exit codes, and output truncation.

Exit criteria:

- Tools can run without a model.
- Tool results have stable JSON shapes for session persistence and events.

## Phase 4: Agent Loop

Goal: run a complete tool-calling agent turn against a fake streaming model.

Tasks:

- Add `agent4j-ai` streaming interfaces.
- Add fake model client in `agent4j-testkit`.
- Implement agent turn loop:
  - build context
  - stream assistant deltas
  - collect tool calls
  - execute tools
  - append tool results
  - continue until terminal stop reason
- Implement prompt, steer, and follow-up queue semantics.
- Implement retry policy for retryable provider errors.
- Implement abort behavior across model stream and tool execution.
- Persist messages through `SessionManager`.

Exit criteria:

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

1. Finish the core message model by adding typed content blocks for assistant
   text, tool calls, and reasoning deltas.
2. Add first tool runtime abstractions from Phase 3: `Tool`, `ToolSpec`,
   `ToolCall`, `ToolResult`, and operation interfaces.
3. Decide and document exact resume semantics for multi-process same-file
   sessions, including stale snapshot handling.
4. Add stronger validation for parent references and malformed typed payloads.
5. Add remaining session helpers for compaction entries once compaction shape is
   finalized.
6. Keep expanding fixtures with real PI session samples as they become
   available.
