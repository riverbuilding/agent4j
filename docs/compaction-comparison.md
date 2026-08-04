# Compaction Comparison

This note compares how PI Agent Harness, Codex, and Claude Code handle context
compaction. It is meant to guide Agent4j's compaction phase.

## Core Concept

Compaction keeps a long-running session usable when the model-visible transcript
becomes too large. Older conversation history is replaced with a summary while
some recent messages are preserved verbatim.

Compaction is not the same as memory. Memory is durable user, project, or
workspace context. Compaction is transcript management.

## PI Agent Harness

PI's implementation is explicit in source:

- `ConversationCompactor`
- `CompactionMiddleware`
- `CompactionConfig`

PI compacts before each reasoning/model call:

1. Separate system messages from conversation messages.
2. Estimate context size by token count and/or message count.
3. If threshold is exceeded, choose a cutoff.
4. Preserve a recent tail verbatim.
5. Summarize the older prefix with a summarization LLM call.
6. Replace working context with:

```text
[compaction summary message] + [preserved recent tail]
```

Important PI details:

- It avoids splitting assistant tool calls from their tool results.
- It can prune older large tool results before summarization.
- It can truncate large tool-call arguments.
- It can flush long-term memories before compacting.
- It can offload full raw messages to session JSONL before compacting.
- On context overflow errors, it can force compaction and retry.

So PI is primarily an internal runtime algorithm: measure, partition, summarize,
rewrite context, and continue.

## Codex

Codex exposes compaction as both user-facing and runtime/API behavior.

User-facing behavior:

- `/compact` summarizes a long chat to free context.
- Codex also compacts chats automatically.
- `/status` can inspect session state.
- `compact_prompt` can override the history compaction prompt.
- `experimental_compact_prompt_file` can load a compaction prompt override from
  a file.

Runtime/API behavior:

- `thread/compact/start` triggers manual history compaction for a thread.
- Progress is streamed through normal `turn/*` and `item/*` notifications.
- Compaction appears as a `contextCompaction` item lifecycle.

Hook behavior:

- `PreCompact` runs before compaction.
- `PostCompact` runs after compaction.
- `SessionStart` can run with source `compact` after compaction.

So Codex treats compaction as a first-class session lifecycle event, not just
hidden cleanup.

Official sources:

- <https://learn.chatgpt.com/docs/developer-commands?surface=cli>
- <https://developers.openai.com/codex/codex-manual.md>

## Claude Code

Claude Code also supports manual and automatic compaction.

User-facing behavior:

- `/compact [instructions]` frees context by summarizing the conversation so
  far.
- Optional instructions can focus the summary.
- `/context` shows what is filling the context window.
- Claude Code automatically compacts as context approaches the limit.

Claude Code documents what survives compaction:

- System prompt and output style stay unchanged.
- Project-root `CLAUDE.md` and unscoped rules are re-injected from disk.
- Auto memory is re-injected from disk.
- Path-scoped rules and nested `CLAUDE.md` are lost until a matching file is
  read again.
- Invoked skill bodies are re-injected, capped by token limits.
- Hooks are not compacted because they run as code, not context.

So Claude Code's documentation focuses heavily on context categories and which
ones are preserved, summarized, lost, or reloaded.

Official sources:

- <https://code.claude.com/docs/en/context-window>
- <https://code.claude.com/docs/en/commands>

## Shared Behavior

All three systems share the same basic flow:

```text
old transcript too large
=> summarize older history
=> keep important state
=> continue with smaller model-visible context
```

They also separate durable context from transient transcript:

- PI separates system messages before compaction.
- Codex keeps settings, hooks, and durable guidance outside ordinary message
  history.
- Claude Code keeps system/output style and reloadable memory/rules separate
  from compacted transcript.

All three support or imply automatic compaction near context limits. Codex and
Claude Code also expose manual compaction commands.

## Differences

PI is the most explicit algorithmically:

- Token/message thresholds.
- Safe cutoff selection.
- Preserved recent tail.
- Tool-call/tool-result pairing.
- Tool-result pruning.
- Raw-message offload.
- Overflow retry.

Codex is more event/API-oriented:

- `/compact`
- automatic compaction
- `thread/compact/start`
- `contextCompaction` lifecycle item
- `PreCompact` and `PostCompact` hooks
- configurable compaction prompt

Claude Code is more user-context-oriented:

- `/context` visualization
- clear rules for what survives compaction
- optional focus instructions on `/compact`
- documented re-injection behavior for memory, rules, and skills

## Agent4j Direction

For Agent4j, the compaction phase should use PI's internal model as the primary
implementation target, while keeping Codex and Claude Code user-facing concepts
in mind.

Target internal shape:

```text
CompactionService
  -> measure model-visible transcript
  -> select safe cutoff
  -> preserve recent tail
  -> summarize prefix
  -> persist compaction entry
  -> rebuild modelMessages
  -> retry on context overflow
```

Target external behavior:

- Manual compaction.
- Automatic threshold compaction.
- Overflow-triggered compaction and retry.
- Compaction started/completed events.
- Configurable summary prompt.
- Context usage/status reporting.

The critical implementation rule is to avoid summarizing or cutting blindly.
Agent4j should preserve PI-style safe partitioning, especially keeping assistant
tool calls together with their corresponding tool results.

## Branch Summary

`BranchSummary` means an agent session branch summary. It is not a Git branch.

Agent4j sessions are stored as a tree of message entries. When a user resumes,
forks, clones, imports, or navigates from one point in the session tree, the
new path is a session branch. A branch summary is a synthetic transcript message
that carries useful context from the source path into the target path.

Example source path:

```text
user: Read README.md
assistant: I will inspect it
tool result: README content
assistant: README says this project is an agent harness
```

A forked or resumed target path can receive:

```text
branchSummary: The source branch inspected README.md and found that the project
is an agent harness. Important unresolved work is ...
```

Then the target branch can continue without replaying the full source path:

```text
user: Now compare it with PI Agent Harness
```

`BranchSummary` and `CompactionSummary` are related but serve different
runtime jobs:

- `CompactionSummary` reduces context size inside the same active conversation
  path.
- `BranchSummary` transfers context from a source session path into another
  forked/resumed/target path.

Both are transcript messages, and both convert to LLM-visible text at the
`convertToLlm` boundary. The persisted role for branch summaries is
`branchSummary`.

## Branch Summary Implementation Layers

Agent4j keeps two request types because the core layer and coding-session layer
own different responsibilities.

`BranchSummaryRequest` is the core request in `agent4j-core`. It is
provider-neutral and session-storage-neutral. It contains:

```text
sessionId
messages
systemPrompt
summaryPrompt
focusInstructions
sourceEntryId
targetSessionId
```

`BranchSummaryService` consumes this request, serializes the source messages,
calls the selected AI provider, and returns a `BRANCH_SUMMARY` `AgentMessage`.
The core layer does not know about JSONL files or `SessionManager`.

`BranchSummaryGenerationRequest` is the coding-runtime request in
`agent4j-coding`. It knows about session persistence and contains:

```text
sourceSessionManager
targetSessionManager
selection
auth
cwd
systemPrompt
summaryPrompt
focusInstructions
options
```

`CodingBranchSummarizer` consumes this request, reads the source session active
path, builds a core `BranchSummaryRequest`, calls `BranchSummaryService`, and
appends the returned `branchSummary` message to the target `SessionManager`.

The layering is:

```text
CodingBranchSummarizer
  -> BranchSummaryGenerationRequest
  -> source SessionManager active path
  -> BranchSummaryRequest
  -> BranchSummaryService
  -> BRANCH_SUMMARY AgentMessage
  -> target SessionManager append
```

This split keeps `agent4j-core` reusable for any runtime while keeping PI-style
session JSONL persistence in `agent4j-coding`.
