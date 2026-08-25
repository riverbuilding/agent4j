# Phase 8 Closeout

Phase 8 closed the known parity gaps that would otherwise leak temporary Java
internals into the SDK, CLI, or extension surfaces.

## Completed Scope

- Provider baseline: `agent4j-ai` now has PI-style generation options,
  timeout/retry ownership, provider/model feature flags, endpoint/base-url
  resolution, and auth-mode-ready resolved auth.
- Conversation ownership: `AgentConversationContext` is the raw loop's canonical
  mutable transcript context; provider-facing `AiMessage` lists are rebuilt only
  at the model boundary, and assistant-only results are derived from generated
  transcript messages.
- Session JSONL: repeated-run append, stale snapshot rejection, parent
  validation, typed payload validation, branch/compaction fixtures, and
  malformed/unknown fixture coverage are pinned.
- Coding tools: built-in tool schemas expose only accepted arguments with
  descriptions, reject extra properties, use stable result keys, and return
  workspace-relative path values where applicable.
- Hook timing: tool hooks are pinned inside the
  `tool_execution_start`/`tool_execution_end` event window, including blocked
  tool results.
- Custom/session prompts: coding `convertToLlm` has pinned Java wrappers for
  bash execution, branch summaries, compaction summaries, and custom extension
  messages.
- Resource/settings parity: supported settings keys and later-phase gaps are
  documented in `docs/resource-settings-parity.md`.
- Documentation cleanup: the duplicate `agent4j-ai/README.md` parity note was
  removed; ADR 0002 and the implementation plan are the authoritative parity
  tracking documents.

## Deferred Work

- Phase 9 owns the PI-style `AgentSession`/`CodingAgentRuntime` API and should
  attach the conversation context to runtime/session ownership rather than
  reintroducing loop-local canonical state.
- Phase 9 also owns the live ChatGPT/Codex subscription login flow. Phase 8 only
  made providers ready for access-token and subscription-style auth resolution.
- Phase 10 owns CLI modes and thin login/logout/status wrappers over the Phase 9
  runtime auth API.
- Phase 12 owns exact extension hook names, discovery, and hook exception
  policy.
- Later source audits still need exact PI text/payload parity for built-in tool
  descriptions, image read payloads, edit multi-replacement behavior, streaming
  render/update details, custom prompt text, and compaction/branch-summary event
  payloads.

## Verification Baseline

Phase 8 closeout should be verified with:

```bash
mvn -q -f pom.xml test
```
