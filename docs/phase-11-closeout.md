# Phase 11 Closeout: Interactive Contract Tests

Phase 11's interactive contract is now covered at the terminal boundary with a
deterministic fake provider. `InteractiveContractTest` exercises provider-backed
prompt submission, streamed assistant text, tool execution, persistent session
creation, command dispatch, model selection, local session selection, session
replacement, and clean shutdown. The lower-level live-session tests continue to
pin steering, follow-up queue consumption, and active-run cancellation in
`LiveAgentSessionControlTest`; renderer tests pin delta/fallback de-duplication
and tool, retry, compaction, queue, and abort status output.

The resulting compatibility level is a usable, injectable line-oriented PI
interactive shell over the shared SDK session/runtime boundary. It is not a
full visual clone of PI's terminal UI.

## Remaining observable gaps

- JLine is present as the terminal rendering layer, but the shell does not yet
  reproduce PI's full editor behavior: history, completion, configurable
  keybindings, Alt+Enter, external editor, and resize-aware editing remain.
- Rich TUI behavior remains intentionally deferred: markdown layout,
  collapsible thinking/tool rows, footer usage, transcript replay, themes, and
  terminal title management are not treated as parity-complete.
- Extension-provided commands/widgets, trust/settings dialogs, and full model
  cycling remain Phase 12 or later work.
- The Java session filename and some global-session discovery details still
  differ from PI's timestamp-plus-ID conventions.
- Production provider endpoint and subscription-login verification remains the
  Phase 9 live-environment gate; fake-provider tests do not close that gap.

These are documented divergences, not blockers for the basic interactive
contract. Any future claim of full PI interactive parity must add headless
editor/TUI tests and production provider evidence.
