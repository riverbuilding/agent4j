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
| `pi -p <prompt>` / non-TTY invocation | Run prompt(s), write final assistant text, then exit | Implemented for explicit `-p` in Slice 3. Automatic non-TTY selection remains a recorded divergence. |
| `pi --mode json <prompt>` | One-shot run that writes a session header and events as JSONL | Implemented in Slice 4. |
| `pi --mode rpc` | Long-lived JSONL command/event process | Implemented in Slice 5. |

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
| `--provider`, `--model`, `--api-key`, `--thinking` | CLI runtime overrides; API keys are runtime-only and never written to session or credential files | Provider/model/API-key selection implemented for the current OpenAI bootstrap; `--thinking` remains unsupported. |
| `--print` / `-p`, `--mode json`, `--mode rpc` | Mode selection above | 2 through 5 |
| `--continue` / `-c`, `--session <path|id>`, `--session-id <id>`, `--fork <path|id>`, `--session-dir`, `--no-session`, `--name` / `-n` | Delegates to `AgentSessionRuntime` and `SessionManager` | Implemented in Slice 6. |
| `--tools` / `-t`, `--exclude-tools` / `-xt`, `--no-tools` / `-nt`, `--no-builtin-tools` / `-nbt` | Validate and construct a tool selection; do not recreate tools inside the CLI mode runner | Implemented in Slice 7. |
| `login`, `logout`, auth status, refresh | Thin command wrappers over `LoginService` | Implemented in Slice 8 as `login`, `logout`, `auth-status`, and `refresh`. |

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
| RPC steering/follow-up delivery occurs after the active SDK prompt rather than inside its live AgentLoop queue | `AgentSession` currently accepts queues only when a prompt starts, while PI mutates the active session queue during streaming. | Add runtime-owned live queue operations in a later runtime/CLI parity slice. |
| Implicit non-TTY selection is not runnable yet | Agent4j requires explicit `--print`, `--mode json`, or `--mode rpc`; PI auto-selects print when stdin or stdout is non-TTY. | Add process TTY detection and PI prompt-input handling in a later CLI parity slice. |
| `--resume` selects the most recent local session | PI presents an interactive local/global session picker; Phase 10 has no terminal UI. | Phase 12 owns picker parity. |
| Session ID search is local to the selected session directory | PI falls back to global project search and requests confirmation before cross-project forking. | Add global session discovery with Phase 12 interaction policy. |
| Current CLI runtime factory constructs OpenAI only | The provider abstraction supports more providers, but CLI provider registry wiring has not expanded beyond Phase 9's OpenAI baseline. | Add configured Anthropic and future-provider bootstrap paths with their provider settings. |
| CLI auth commands expose the Phase 9 OpenAI browser flow but do not prove production endpoints | Browser/OAuth transport correctness needs a real subscription interaction outside CI. | Keep Phase 9's opt-in live test and production endpoint verification as the closure evidence. |
| Agent4j session filenames use random UUIDs | PI names new session files with a timestamp and session ID. Both are JSONL sessions under the same cwd-derived directory, but the file artifact is not identical. | Align naming only after session ID/file-name compatibility is explicitly tested. |

## Phase 10 Closeout

Slices 1-9 are complete. The root command, injectable I/O, runtime factory,
print runner, JSON runner, and RPC runner pin lowercase mode parsing,
repeated-mode last-value acceptance, settings model resolution, ephemeral
`--api-key` behavior, final-text stdout, PI-shaped JSONL event ordering,
streaming deltas, correlated protocol responses, malformed-input recovery,
orderly shutdown, provider failures, cancellation, and temporary-session
cleanup. Session lifecycle flags now delegate to SDK operations, and typed tool
selection filters the runtime-owned registry with strict argument validation.
Auth subcommands delegate to Phase 9 `LoginService` without token-bearing
stdout output. The closeout suite covers print success/tool/failure/abort,
JSON event serialization/failure/abort, RPC framing/recovery/shutdown/session
commands, session create/resume/fork/continue/conflict handling, tool selection,
and sanitized auth commands. Session conflicts are validated before runtime
creation, matching PI bootstrap ordering.

The Phase 10 implementation is complete, but it does not claim the recorded
divergences above are closed. In particular, live RPC queue mutation, automatic
non-TTY selection, the interactive resume picker, global session discovery,
multi-provider CLI construction, package/extension commands, and the remaining
RPC command set belong to their respective later phases. Phase 9 production
OAuth verification also remains independent of this CLI closeout.
