# PI CLI Shape Audit

## Scope And Evidence

This audit pins the Phase 10 CLI target to PI coding agent `0.82.1`. It covers
the public process contract, not a TypeScript implementation transplant.

Primary references:

- [CLI parser](https://raw.githubusercontent.com/earendil-works/pi/v0.82.1/packages/coding-agent/src/cli/args.ts)
- [CLI bootstrap and mode selection](https://raw.githubusercontent.com/earendil-works/pi/v0.82.1/packages/coding-agent/src/main.ts)
- [print and JSON runner](https://raw.githubusercontent.com/earendil-works/pi/v0.82.1/packages/coding-agent/src/modes/print-mode.ts)
- [JSON event-stream contract](https://github.com/earendil-works/pi/blob/v0.82.1/packages/coding-agent/docs/json.md)
- [RPC protocol](https://github.com/earendil-works/pi/blob/v0.82.1/packages/coding-agent/docs/rpc.md)

`agent4j-cli` now has `Agent4jCli`, `Agent4jRootCommand`, and an injectable
`CliRuntimeFactory`. `DefaultCliRuntimeFactory` discovers resources/settings,
builds `CodingAgentRuntimeServices` with `CodingTools`, resolves the configured
OpenAI model, and returns `CodingAgentSessionRuntime`. It does not construct an
agent loop or provider execution path in the CLI module. The Phase 9
`AgentSessionRuntime` and `LoginService` remain the only owners of session and
authentication behavior.

## PI Command Surface

PI has three process modes:

| PI invocation | Semantics | Phase 10 target |
| --- | --- | --- |
| `pi` | Interactive terminal session | Defer terminal UI to Phase 12. Phase 10 supplies only the root parsing and runtime boundary it will use. |
| `pi -p <prompt>` / non-TTY invocation | Run prompt(s), write final assistant text, then exit | Implemented for explicit `-p` in Slice 3. Non-TTY auto-selection arrives with JSON/process I/O work. |
| `pi --mode json <prompt>` | One-shot run that writes a session header and events as JSONL | Implemented in Slice 4. |
| `pi --mode rpc` | Long-lived JSONL command/event process | Implement in Slice 5. |

PI also has package-management commands (`install`, `remove`, `uninstall`,
`update`, `list`, `config`). They depend on PI package and extension machinery.
They are outside Phase 10 and must not be represented as available agent4j CLI
commands before Phase 11 and package-management parity work exist.

## Mode Selection And Input

PI resolves mode in this order:

1. `--mode rpc` selects RPC.
2. `--mode json` selects JSON.
3. `--print` / `-p`, non-TTY stdin, or non-TTY stdout selects one-shot print.
4. Otherwise it starts the interactive terminal mode.

The parser accepts `--mode text`, but it does not force one-shot output: with
both streams attached to a terminal PI still selects interactive mode. Repeated
`--mode` flags follow parser order, so the last accepted value determines the
resolved mode. Agent4j will preserve this observable behavior when Slice 2
introduces its Picocli command specification.

For print and JSON modes, PI reads piped stdin as UTF-8 text and combines it
with positional prompt input. `@file` arguments are expanded into the initial
prompt, including image attachments. RPC reserves stdin for commands and
rejects `@file` arguments at startup.

## Flag And Session Contract

The initial Phase 10 flag surface is intentionally limited to runtime features
already implemented in agent4j:

| PI flags | Required behavior | Planned slice |
| --- | --- | --- |
| `--provider`, `--model`, `--api-key`, `--thinking` | CLI runtime overrides; API keys are runtime-only and never written to session or credential files | 2 and 7 |
| `--print` / `-p`, `--mode json`, `--mode rpc` | Mode selection above | 2 through 5 |
| `--continue` / `-c`, `--session <path|id>`, `--session-id <id>`, `--fork <path|id>`, `--session-dir`, `--no-session`, `--name` / `-n` | Delegate to `AgentSessionRuntime` and `SessionManager` | 6 |
| `--tools` / `-t`, `--exclude-tools` / `-xt`, `--no-tools` / `-nt`, `--no-builtin-tools` / `-nbt` | Validate and construct a tool selection; do not recreate tools inside the CLI mode runner | 7 |
| `login`, `logout`, status, refresh | Thin command wrappers over `LoginService` | 8 |

Scalar options use last-occurrence-wins parsing in PI. Repeated additive options
such as `--append-system-prompt`, `--extension`, `--skill`, prompt templates,
and themes preserve encounter order. Phase 10 must preserve last-wins behavior
for its supported scalar flags; unsupported extension/resource flags must fail
clearly rather than be silently accepted.

PI validates session combinations before runtime creation. `--fork` cannot be
combined with `--session`, `--continue`, `--resume`, or `--no-session`.
`--session-id` cannot be combined with `--session`, `--continue`, or `--resume`.
`--session` resolves a path first, then an exact/partial local ID, then an
exact/partial global ID. A session selected from another project requires an
interactive fork confirmation.

## Output And Exit Contract

| Mode | stdout | stderr | exit behavior |
| --- | --- | --- | --- |
| Print | Final assistant text blocks, each newline-terminated | Diagnostics, startup failures, and assistant error/abort messages | `0` on completion; `1` for assistant error/abort or runner failure |
| JSON | First persistent-session header, then one JSON object per event | Diagnostics only | One-shot runner returns nonzero on failures |
| RPC | JSONL responses and events only | Diagnostics only | Runs until stdin closes, shutdown command completes, or a process signal is handled |

Startup validation errors, invalid flag combinations, missing non-interactive
model configuration, and unsupported RPC file arguments use exit code `1` in
PI. Metadata commands such as help/version/list-models complete with `0`.
Phase 10 tests must assert stdout, stderr, and exit code separately.

## JSON Event Envelope

JSON mode writes a session header before events for persistent sessions. Its
event stream uses PI event names such as `agent_start`, `turn_start`,
`message_start`, `message_update`, `message_end`, `tool_execution_start`,
`tool_execution_update`, `tool_execution_end`, `turn_end`, and `agent_end`.
`message_update` contains the current reconstructed assistant message plus its
delta-only `assistantMessageEvent`; it does not expose a provider-specific
partial snapshot. `message_end` holds the authoritative completed message.

Agent4j has an internal `AgentEvent` hierarchy, but its final compaction and
branch-summary payload audit is still open in ADR 0002. Slice 4 must add a
dedicated CLI JSON serializer that maps the public PI field names and removes
non-PI internal fields; it must not expose Java record names or rely on default
Jackson polymorphic serialization.

## RPC Envelope

RPC is strict LF-delimited JSONL. Each input object can include an optional
`id`; the corresponding response preserves that `id`:

```json
{"id":"req-1","type":"prompt","message":"Hello"}
{"id":"req-1","type":"response","command":"prompt","success":true}
```

Events share stdout with responses. A response accepting a prompt only confirms
that it was accepted or queued; later execution failures arrive through normal
events/messages. Parse failures return a `response` with `command: "parse"`
and `success: false`, then keep the process alive.

The PI protocol is larger than Phase 10's initial runtime surface. The required
first implementation set is `prompt`, `steer`, `follow_up`, `abort`,
`new_session`, `get_state`, `get_messages`, `set_session_name`, and `shutdown`,
plus correlated success/error responses. Model switching, tree navigation,
compaction, direct bash, extension commands, and extension UI requests require
additional runtime/extension capabilities and remain explicitly unsupported
until their owning phase supplies them. Unsupported commands must receive a
structured unsuccessful response, never a silent drop or a process crash.

## Intentional Java Divergences

| Divergence | Reason | Follow-up |
| --- | --- | --- |
| Picocli command model rather than PI's handwritten two-pass parser | Java process integration needs type-safe command binding; behavior, precedence, output, and exit contracts are the compatibility target. | Slice 2 tests pin externally observable precedence. |
| No interactive terminal/TUI or `--resume` picker in Phase 10 | PI's resume picker and project-crossing confirmation require the Phase 12 terminal UI. | Phase 10 supports explicit `--session` and noninteractive lifecycle operations; Phase 12 owns picker parity. |
| No package-management commands or extension-defined CLI flags | `agent4j` does not yet implement PI package management or extension discovery/execution. | Phase 11 and later package parity work. |
| Initial RPC subset | Several PI RPC commands depend on model catalog mutation, session tree UI semantics, direct bash, and extensions. | Slice 5 establishes framing and core session/prompt commands, then expand from audited PI commands. |
| RPC steering/follow-up delivery occurs after the active SDK prompt rather than inside its live AgentLoop queue | `AgentSession` currently accepts queues only when a prompt starts, while PI mutates the active session queue during streaming. | Add runtime-owned live queue operations before Phase 10 closeout. |
| Implicit non-TTY selection is not runnable yet | Slices 3-5 implement explicit print, JSON, and RPC modes; automatic process-mode selection remains absent. | Slice 6 closes process/session lifecycle selection. |
| Print mode uses a temporary session | PI persists print-mode sessions by default, while session path, continue, resume, fork, and no-session flags are not implemented yet. | Slice 6 must replace temporary storage with PI-compatible session lifecycle selection. |

## Slice 1 Result

Slices 1-5 are complete. The root command, injectable I/O, runtime factory,
print runner, JSON runner, and RPC runner pin lowercase mode parsing,
repeated-mode last-value acceptance, settings model resolution, ephemeral
`--api-key` behavior, final-text stdout, PI-shaped JSONL event ordering,
streaming deltas, correlated protocol responses, malformed-input recovery,
orderly shutdown, provider failures, cancellation, and temporary-session
cleanup.
