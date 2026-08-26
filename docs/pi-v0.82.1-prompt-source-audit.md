# PI v0.82.1 Prompt Source Audit

This document pins the upstream source for the prompt-compatibility work. It is
an extraction inventory, not an implementation: no agent4j runtime behavior is
changed by this audit.

## Pinned upstream

| Field | Value |
| --- | --- |
| Repository | `earendil-works/pi` |
| Release tag | `v0.82.1` |
| Immutable commit | `b4f293684bba718d59cc1157679bcf6157b3a7f5` |
| Archive inspected | `https://codeload.github.com/earendil-works/pi/tar.gz/refs/tags/v0.82.1` |
| Tag resolution | `https://api.github.com/repos/earendil-works/pi/git/refs/tags/v0.82.1` |

All links below use that commit, never `main` or the mutable tag name. The
SHA-256 values make the exact extracted source files independently checkable.

## Runtime prompt inventory

| ID | Built-in source | Rendered into | Conditions / composition |
| --- | --- | --- | --- |
| `coding-system` | [`core/system-prompt.ts`](https://github.com/earendil-works/pi/blob/b4f293684bba718d59cc1157679bcf6157b3a7f5/packages/coding-agent/src/core/system-prompt.ts) `buildSystemPrompt` | The normal model request's system prompt | Default baseline; active tool snippets and guidelines; optional append prompt; project context; eligible skills; then working directory. A custom system prompt replaces only the default baseline. |
| `tool-guidance` | `core/tools/{bash,edit,read,write}.ts` | `coding-system` available-tools and guidelines sections | Only selected tools participate. A tool needs a nonempty `promptSnippet` to appear in Available tools. Guidelines are collected in selected-tool order, trimmed and deduplicated. |
| `skill-index` | [`core/skills.ts`](https://github.com/earendil-works/pi/blob/b4f293684bba718d59cc1157679bcf6157b3a7f5/packages/coding-agent/src/core/skills.ts) `formatSkillsForPrompt` | `coding-system` | Appended only when `read` is selected and at least one skill is loaded. The body of a skill is deliberately not injected; the prompt receives a name, description, and path and must read it. |
| `context-files` | `core/system-prompt.ts` | `coding-system` | Appended after the append prompt in `<project_context>` / `<project_instructions path="…">` markup. Paths and contents are interpolated without XML escaping. |
| `compaction-system` | [`core/compaction/utils.ts`](https://github.com/earendil-works/pi/blob/b4f293684bba718d59cc1157679bcf6157b3a7f5/packages/coding-agent/src/core/compaction/utils.ts) `SUMMARIZATION_SYSTEM_PROMPT` | A standalone compaction or branch-summary model request | Always the system prompt for all three compaction variants and branch summaries. |
| `compaction-initial` | [`core/compaction/compaction.ts`](https://github.com/earendil-works/pi/blob/b4f293684bba718d59cc1157679bcf6157b3a7f5/packages/coding-agent/src/core/compaction/compaction.ts) `SUMMARIZATION_PROMPT` | Compaction request user message | Used when there is no previous compaction summary. |
| `compaction-update` | `core/compaction/compaction.ts` `UPDATE_SUMMARIZATION_PROMPT` | Compaction request user message | Used when a previous compaction summary exists; the prior summary is wrapped in `<previous-summary>`. Optional custom instructions append as `Additional focus: …`. |
| `compaction-turn-prefix` | `core/compaction/compaction.ts` `TURN_PREFIX_SUMMARIZATION_PROMPT` | A second compaction request user message | Used only when the cut point splits a turn. |
| `branch-summary` | [`core/compaction/branch-summarization.ts`](https://github.com/earendil-works/pi/blob/b4f293684bba718d59cc1157679bcf6157b3a7f5/packages/coding-agent/src/core/compaction/branch-summarization.ts) `BRANCH_SUMMARY_PROMPT` | Branch-summary request user message | Default instructions; custom instructions either replace them or append as `Additional focus: …`. Its result is prefixed with `BRANCH_SUMMARY_PREAMBLE`. |

For the normal turn, [`core/agent-session.ts`](https://github.com/earendil-works/pi/blob/b4f293684bba718d59cc1157679bcf6157b3a7f5/packages/coding-agent/src/core/agent-session.ts) `_rebuildSystemPrompt` is the call site that preserves selected-tool order, joins discovered append prompts with two newlines, and calls `buildSystemPrompt`. It rebuilds the base prompt after active-tool or resource changes. A per-session override supersedes that rebuilt base prompt at request time.

### Exact default-tool extract

| Tool | Prompt snippet | Prompt guidelines |
| --- | --- | --- |
| `read` | `Read file contents` | `Use read to examine files instead of cat or sed.` |
| `bash` | `Execute bash commands (ls, grep, find, etc.)` | Only when `exposeSessionEnvironment`: `Inspect PI_* environment variables for current model and session details.` |
| `edit` | `Make precise file edits with exact text replacement, including multiple disjoint edits in one call` | `Use edit for precise changes (edits[].oldText must match exactly)`; `When changing multiple separate locations in one file, use one edit call with multiple entries in edits[] instead of multiple edit calls`; `Each edits[].oldText is matched against the original file, not after earlier edits are applied. Do not emit overlapping or nested edits. Merge nearby changes into one edit.`; `Keep edits[].oldText as small as possible while still being unique in the file. Do not pad with large unchanged regions.` |
| `write` | `Create or overwrite files` | `Use write only for new files or complete rewrites.` |

The builder adds `Use bash for file operations like ls, rg, find` only when
`bash` is active and `grep`, `find`, and `ls` are all inactive. It then adds
the selected-tool guidelines, followed by `Be concise in your responses` and
`Show file paths clearly when working with files`.

## Extraction boundary

PI v0.82.1 does **not** ship built-in model prompts for title/session-name
generation, retry/error recovery, print/JSON/RPC/interactive modes, or prompt
templates. Session naming is user-provided state, retry uses provider/runtime
errors, and the execution modes send the same `AgentSession` prompt.

[`core/prompt-templates.ts`](https://github.com/earendil-works/pi/blob/b4f293684bba718d59cc1157679bcf6157b3a7f5/packages/coding-agent/src/core/prompt-templates.ts) is a loader and `$`-argument substitution engine, not a collection of built-in templates. It loads `.md` resources from global, project, explicit, and package paths. Likewise, skills and project instructions are injected resources, not built-in prompt text. Examples and tests were excluded from this inventory.

The generic `packages/agent/src/harness/*` prompt/compaction sources were also
excluded: the PI coding-agent runtime imports and executes its own
`packages/coding-agent/src/core/*` implementations above.

## Extracted-source checksums

| Source path | SHA-256 |
| --- | --- |
| `packages/coding-agent/src/core/system-prompt.ts` | `677c3cf2ca259d15c27466961702a489244e9505829a6c994d0de314b2a469ef` |
| `packages/coding-agent/src/core/agent-session.ts` | `4fb0c7cafa450588a8786617f2212c08356160af325c137b8cc7f065a4a9bc9e` |
| `packages/coding-agent/src/core/tools/bash.ts` | `ef864e7c0518089e849a31a02de31c33abb03ad55114c954d9e0ba5cc83576d5` |
| `packages/coding-agent/src/core/tools/edit.ts` | `a42f745488ab89553173479986efc222544eeac26e1820692954031c63c27fdd` |
| `packages/coding-agent/src/core/tools/read.ts` | `1acc6fcb88a29317d4f800fa1e9e1ff3d13d32527dd8c6f1cdbeeab5107ae06d` |
| `packages/coding-agent/src/core/tools/write.ts` | `7f7f1425e2a7cdffe48b190033189fd03dffc5756708b12775dddb0acfe90c6c` |
| `packages/coding-agent/src/core/skills.ts` | `2f56a637c7231f112331a9636ccef2d1d2ae36fc2fee3916b892e053a63ff8f6` |
| `packages/coding-agent/src/core/prompt-templates.ts` | `e94b8504b97fe668b04577891b7029abc7d11ac795e728982d2615a13ec1528a` |
| `packages/coding-agent/src/core/compaction/utils.ts` | `6e9d2c0b6076d5cac5fd444e5381a1fc01da5acf30eb776319a6b562fc42a02c` |
| `packages/coding-agent/src/core/compaction/compaction.ts` | `0ce643e2e3c97e4dc160e888e93cb8b5b53914e609a6d333765493d630b6b731` |
| `packages/coding-agent/src/core/compaction/branch-summarization.ts` | `9b0af541098b1f8346c1cb8bcbe51b6194288a10cf55dc0365a71fd8d41e9b9b` |

## Follow-on implementation contract

The next slice should treat this document as the source inventory and port the
`coding-system`, `tool-guidance`, `skill-index`, and `context-files` entries
first. Its fixture should use a non-custom prompt with the four default tools,
a representative project context file, skills, append prompt, and a normalized
working directory. A separate fixture must cover custom-prompt replacement and
the `read`-absent case, because those are the main conditional branches.
