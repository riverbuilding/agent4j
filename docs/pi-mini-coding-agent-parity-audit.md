# PI Mini Coding-Agent Parity Audit

## Purpose

This audit assesses whether agent4j can currently operate as a small,
reliable coding agent for simple projects. The comparison target is PI's coding
agent harness rather than a line-by-line TypeScript port. It focuses on the
capabilities that determine whether an agent can inspect a workspace, make a
safe change, run verification, and report the outcome.

The conclusion is that agent4j has a strong runtime foundation but is not yet a
dependable end-to-end mini coding agent. Phase 15 Slice 1 closes the original
default coding-agent prompt blocker; tool reliability and end-to-end acceptance
validation are now the highest-priority remaining work.

## PI reference behavior

PI supplies a coding-agent prompt and gives models four default tools: `read`,
`write`, `edit`, and `bash`. It supports project or global system-prompt
replacement, and its maintained provider catalog covers multiple providers and
tool-capable models.

Primary public references:

- [PI coding-agent README](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/README.md)
- [PI extension documentation](https://github.com/badlogic/pi-mono/blob/main/packages/coding-agent/docs/extensions.md)
- [agent4j PI compatibility contract](pi-compatibility.md)
- [agent4j CLI shape audit](pi-cli-shape-audit.md)

## Current capability assessment

| Area | Current state | Parity and mini-agent impact |
| --- | --- | --- |
| Agent loop and sessions | Persisted JSONL sessions, tool calls, streaming, retries, compaction, steering/follow-up, abort, events, print/JSON/RPC, and a basic interactive shell are implemented. | A good runtime foundation. |
| Built-in coding tools | `read`, `write`, `edit`, `bash`, `ls`, `grep`, and `find` are available. | Can perform simple workspace tasks, with behavioral gaps described below. |
| Default coding prompt | `agent4j-coding-v1` is composed with PI-style selected-tool listing/guidelines, project instructions, eligible skills, and explicit CLI overrides. | Implemented in Phase 15 Slice 1. |
| Resource discovery | Discovers context files, system/append prompts, skills, templates, themes, settings, and applies non-interactive trust gates. | Discovery is stronger than the current invocation wiring. |
| Providers and models | Native adapters exist for OpenAI Responses and Anthropic Messages, with six shipped model IDs. | Much narrower than PI's multi-provider maintained catalog. |
| Interactive CLI | Basic line shell supports core prompts, sessions, model selection, and commands. | Suitable for a narrow mini-agent release, not PI TUI/editor parity. |
| Extensions | Java-only SPI supports explicit and application-classpath ServiceLoader extensions, tools, hooks, lifecycle listeners, commands, and project-trust gating. | Intentional first-release boundary; PI TypeScript/package compatibility remains deferred. |
| Verification | Deterministic fake-provider tests cover substantial runtime behavior. Production-provider evidence remains incomplete. | A real-model coding-task test is required before claiming the mini-agent goal. |

## Critical gaps

### Default system prompt is on the normal CLI path

Phase 15 Slice 1 adds the versioned `agent4j-coding-v1` baseline and composes
it with selected-tool guidance, discovered `SYSTEM.md`, `APPEND_SYSTEM.md`,
context files, and model-visible skills. A discovered or explicit system prompt
replaces the baseline, while tool guidance and the remaining context stay
additive.

Normal CLI construction now resolves that prompt and passes it through print,
JSON, RPC, and interactive `PromptRequest` creation. `--system-prompt` replaces
the baseline/discovered system prompt; repeatable `--append-system-prompt`
provides explicit additive instructions.

OpenAI and Anthropic request-serialization tests verify the composed prompt is
sent to each provider. This removes the first mini-agent usability blocker.

### Provider and model coverage is narrow

`BuiltInProviderCatalog` ships only:

- OpenAI: `gpt-5`, `gpt-5-mini`, `gpt-4.1`
- Anthropic: `claude-sonnet-4-5`, `claude-opus-4-5`, `claude-haiku-4-5`

`models.json` can add model identifiers to the existing providers but does not
create a transport for a new provider. A supplied Java provider is needed for
any additional provider ID. This is not comparable to PI's broad provider and
model coverage.

### Tool behavior needs hardening

The existing tools are useful, but do not yet consistently match PI's coding
tool behavior:

- All seven tools are currently registered by default; PI defaults to four
  write-capable tools and exposes the read-only set separately.
- `read` is UTF-8 text-only and lacks image support and practical
  offset/limit controls for large files.
- `edit` replaces the first occurrence, then reports ambiguity. A safer
  contract rejects ambiguous changes before writing.
- `grep` and `find` use Java traversal and regular expressions rather than
  `.gitignore`-aware behavior.
- `bash` runs local `/bin/sh -lc` commands without a permission or sandbox
  layer.
- `LocalProcessOps` waits for completion before draining stdout and stderr. A
  verbose process can fill pipe buffers and stall before the timeout.
- Path normalization blocks simple workspace escapes, but no real-path check
  protects writes through symlinks that point outside the workspace.

These gaps do not prevent a prototype, but they reduce reliability and safety
for normal coding tasks.

### Resources are discovered but not fully invoked

The resource layer supports project/global system prompts, context files,
skills, templates, themes, settings, and trust gating. The ordinary CLI path
now connects the assembled prompt to requests. Prompt templates are still not
exposed as CLI/interactive commands.

### CLI and interactive gaps

The basic shell is enough for a constrained release. Remaining differences
include non-TTY/stdin behavior, `@file` and image attachment support, thinking
controls, completion/history/multiline editing, full session-tree navigation,
trust/settings dialogs, rich transcript/tool rendering, and full model cycling.

### Intentional extension and package gaps

The Phase 13 boundary intentionally excludes TypeScript execution, Node or
other subprocess extension execution, package installation/update/reconcile,
dynamic project-code loading, project-local extension discovery, extension UI,
and PI package compatibility. These belong to Phase 14 design work and are not
required for a Java-only mini-agent release.

## Recommended mini-agent milestone

Do not use “full PI parity” as the immediate release goal. Define a narrower
milestone: **simple-project coding agent**.

The agent should be able to:

1. Open a small fixture project.
2. Receive a feature request through the CLI.
3. Inspect relevant files with coding tools.
4. Modify the project.
5. Run its test or build command.
6. Repair one intentional test failure when needed.
7. Report changed files and the final verification result.

The acceptance test should run deterministically with a fake provider and as
an opt-in, credential-gated smoke test against at least one real OpenAI model
and one real Anthropic model. It must use a bounded cost/token budget and reset
the fixture after every run.

## Recommended implementation order

1. **Wire a default coding-agent prompt.** Compose a built-in baseline,
   selected-tool guidance, project/global prompt replacement or append files,
   `AGENTS.md` context, and eligible skills. Assert the exact provider request
   body in tests.
2. **Add the end-to-end coding-agent fixture.** First exercise the full path
   with a deterministic fake provider, then add opt-in real-provider smoke
   tests.
3. **Harden tools.** Concurrently drain bash output, reject ambiguous edits
   before mutation, add large-file controls, choose an image strategy, and
   improve path/symlink safety. Define the default versus read-only tool sets.
4. **Broaden provider coverage pragmatically.** Add a configurable
   OpenAI-compatible adapter, retain native Anthropic, then add providers based
   on user demand. Keep catalog data separate from provider transports.
5. **Finish essential CLI wiring.** Add system-prompt flags, proper stdin and
   non-TTY handling, prompt-template invocation, and explicit model listing.

## Decision guide

The Phase 13 Java-only extension design is not the near-term blocker. The
highest-value work for a usable mini coding agent is default prompt wiring and
real end-to-end coding-task verification. Broad PI provider/package/TUI parity
can then proceed independently from a working minimal agent.
