# PI Interactive Shape Audit

## Scope And Evidence

This audit pins the Phase 11 interactive target to PI coding agent `0.82.1`.
It defines the observable and ownership contract for a usable Java interactive
shell. It is not a plan to transplant PI's TypeScript UI implementation.

Primary references:

- [Interactive mode](https://raw.githubusercontent.com/earendil-works/pi/v0.82.1/packages/coding-agent/src/modes/interactive/interactive-mode.ts)
- [CLI bootstrap and mode selection](https://raw.githubusercontent.com/earendil-works/pi/v0.82.1/packages/coding-agent/src/main.ts)
- [CLI argument parser](https://raw.githubusercontent.com/earendil-works/pi/v0.82.1/packages/coding-agent/src/cli/args.ts)
- [Built-in slash commands](https://raw.githubusercontent.com/earendil-works/pi/v0.82.1/packages/coding-agent/src/core/slash-commands.ts)

PI's `InteractiveMode` owns terminal presentation and user interaction, while
`AgentSessionRuntime` owns the active `AgentSession`, resources, model runtime,
session manager, and agent behavior. It does not create a second agent loop.
Agent4j must retain the same boundary: `agent4j-cli` renders and translates
input; `AgentSessionRuntime` and `AgentSession` remain owners of conversation,
session persistence, events, tools, compaction, and authentication.

## Startup And Mode Selection

PI resolves interactive mode when neither RPC/JSON/print nor non-TTY input or
output selects a process mode. It validates session flags before runtime
creation, selects the target session before resolving cwd-bound resources, and
can ask interactively when a selected session's original cwd is absent. It then
creates `InteractiveMode`, initializes the terminal, and runs it. Piped input
switches an otherwise interactive invocation to print mode.

Phase 11 requirements:

1. The root CLI must dispatch terminal-attached `--mode text`/default execution
   to an interactive runner.
2. The runner must obtain its session through `CliSessionLifecycle` and
   `AgentSessionRuntime`, including new, continue, explicit session, fork, and
   no-session behavior already implemented for Phase 10.
3. Terminal input/output, terminal capability detection, and shutdown signals
   must be injectable so fake-provider tests do not require a real terminal.
4. Initial positional prompts may be submitted after startup, but stdin piping
   remains process-mode behavior and must not be consumed by the interactive
   editor.

The missing-cwd confirmation and interactive trust prompt are selectors for
later slices; the first shell may report a clear diagnostic until their session
and trust flows exist.

## Prompt Submission And Live Queue

PI uses one active session. A normal editor submission calls session prompt
behavior. While streaming, Enter adds a `steer` message and Alt+Enter adds a
`followUp` message. PI displays pending messages, lets users restore them to
the editor, and aborting restores pending messages before aborting the agent.
During compaction it retains messages locally, then sends them to the session
with their original queue mode after compaction/retry handling.

Agent4j requirements:

- The first shell submits a normal prompt through `AgentSession.prompt(...)`.
- It subscribes before submission and renders the session event stream rather
  than printing `PromptResult` as a second transcript.
- Ctrl-C/interrupt aborts the active `AbortController`; it must not terminate
  the entire shell on the first interrupt.
- True PI steering/follow-up requires runtime-owned live queue operations on
  `AgentSession`. The current RPC backlog drains only after a prompt completes,
  so it is not sufficient for interactive parity. Slice 5 must add that runtime
  capability before exposing Enter/Alt+Enter queue controls as PI-compatible.

## Event Rendering

PI subscribes to `AgentSession` events and maintains UI state for streaming
assistant content, tool executions, queue updates, retry/compaction status,
session info, and footer usage. The authoritative completed assistant message
arrives through the event stream; a renderer must not append it again after
printing deltas. Tool rows and thinking blocks have separate live and replayed
display policies.

The Phase 11 rendering progression is deliberately smaller:

| Stage | Required rendering |
| --- | --- |
| Basic shell | User prompt, assistant text, provider/tool failure, abort, and a stable prompt boundary. |
| Streaming | `message_start`/`message_update`/`message_end` deltas without duplicate final text; concise tool start/update/end and retry/compaction status. |
| Rich TUI | Markdown, collapsible thinking/tool rows, footer/context usage, transcript replay, terminal title, themes, and selectors. |

`AgentEvent` remains the Java rendering input. `JsonEventSerializer` is not a
terminal renderer and must not become the interactive UI protocol.

## Commands, Selectors, And Cancellation

PI's editor supports slash-command completion and built-in commands. Its
interactive surface includes session selection with local/global session lists,
model selection and model cycling, session tree navigation, compact/status
operations, login/settings/trust dialogs, and extension-provided commands.
Its keybindings include submit, interrupt, exit, model selection/cycling,
follow-up/dequeue, tool/thinking visibility, external editor, and shell input.

The Java implementation order was:

1. `/help`, `/exit`, `/new`, `/abort`, `/status`, and `/compact` where the
   backing runtime operation already exists.
2. `/session` and `/model` selection once the corresponding runtime APIs can
   enumerate/select them without CLI-owned persistence logic. This slice is
   implemented through `CliSessionLifecycle` and the provider registry.
3. Completion, history, keybinding configuration, trust/login/settings dialogs,
   extension commands, tree navigation, and external-editor support after the
   basic shell contract is stable.

An interrupt first cancels autocomplete/editor activity or the active agent
run. Exit performs ordered unsubscription, terminal restoration, and session
resource cleanup. A second interrupt/explicit exit may terminate the process
only after the active operation is dealt with.

## Java Terminal Decision

Phase 11 uses **JLine 3** behind the terminal rendering boundary. The initial
contract remains injectable buffered line I/O so prompt/session/error/EOF
behavior is deterministic in tests; JLine-backed ANSI rendering is selected
for capable terminals, while plain line rendering remains the fallback.

No rich TUI library is selected in this slice. PI uses its own differential
`@earendil-works/pi-tui`; JLine alone is not a comparable component framework.
After the streaming line shell is stable, Phase 11 will evaluate a Java rich
renderer against these requirements: incremental redraw without corrupting
assistant output, Unicode width/wrapping, resize handling, keyboard input,
headless testing, and Windows/macOS/Linux behavior. The selected renderer must
remain behind an `InteractiveTerminal`/renderer boundary, with line-mode as a
fallback.

## Intentional Initial Divergences

| Difference | Reason | Follow-up |
| --- | --- | --- |
| Basic line shell before full-screen TUI | The immediate goal is a usable interactive agent over the existing runtime, not a visual clone. | Add streaming renderer, then evaluate a rich TUI library. |
| No extension commands/widgets in the first shell | Extension SPI is Phase 12 after the interactive contract is established. | Add command registration and UI hooks in Phase 12. |
| Buffered line input cannot express PI's Alt+Enter follow-up keybinding | The contract suite can exercise `/follow-up`, but the injected line reader has no key-event model. | Add JLine key maps and editor-level follow-up/dequeue coverage. |
| No interactive project-trust or missing-cwd confirmation in the first shell | Java has only non-interactive trust gating today. | Add selector-backed trust and cwd decisions with session selection. |
| No PI-specific custom TUI implementation | PI's `pi-tui` is TypeScript-specific. | Preserve external behavior with Java interfaces rather than porting internals. |

## Slice 1 Result

The Phase 11 target is now pinned: build a JLine-backed, testable line shell in
`agent4j-cli` over `AgentSessionRuntime`; do not create an interactive-only
loop, session manager, provider path, or auth path. Rich terminal presentation
and extension UI are later layers. Live steering/follow-up is implemented at
the runtime boundary and covered by `LiveAgentSessionControlTest`; the
remaining queue divergence is terminal keybinding fidelity.

## Slice 9 Closeout

`InteractiveContractTest` now drives the terminal host with a fake provider and
covers streamed prompts, tool execution, persisted sessions, commands, model
selection, session selection/replacement, and shutdown. Existing renderer and
live-session tests cover event de-duplication, tool/status rows, queue updates,
steering, follow-up, and cancellation. The basic observable contract is
complete for the scoped line shell. Full PI parity remains open for editor
history/completion/key maps, rich TUI layout and replay, extension UI,
timestamp-plus-ID session filenames, and production provider/live-login
verification; see `docs/phase-11-closeout.md`.
