# PI Compatibility Contract

This document defines the PI behavior agent4j intends to preserve.

## Compatibility Target

The initial target is the public behavior of
`@earendil-works/pi-coding-agent` around version `0.82.x`.

agent4j is compatible when it can read, write, and reason over PI-shaped
sessions and expose the same harness concepts through Java APIs and CLI modes.

## Preserved Artifacts

### Session JSONL

Session files are append-only JSONL documents. The first entry is a session
header. Subsequent entries form a tree through `id` and `parentId`.

Required entry types:

- `session`
- `message`
- `model_change`
- `thinking_level_change`
- `compaction`
- `session_info`
- `file`
- `custom`

Required message roles:

- `user`
- `assistant`
- `toolResult`
- `bashExecution`
- `custom`
- `branchSummary`
- `compactionSummary`

### Agent Session API

The Java API must model these PI concepts:

- prompt submission
- steering queue
- follow-up queue
- event subscription
- active model and thinking level
- abort
- manual compaction
- session tree navigation

### Built-In Tools

The first tool parity set is:

- `read`
- `write`
- `edit`
- `bash`

The second set is:

- `ls`
- `grep`
- `find`

Tool implementations must be deterministic under test by depending on
filesystem and process operation interfaces.

### Runtime Modes

Implementation order:

1. JSON mode
2. Print mode
3. RPC mode
4. Basic interactive shell
5. Rich terminal UI

### Resource Discovery

agent4j should support PI-style discovery for:

- global agent dir
- project `.pi`
- `AGENTS.md`
- `CLAUDE.md`
- `SYSTEM.md`
- `APPEND_SYSTEM.md`
- prompt templates
- skills
- themes

## Deliberately Deferred

- native TypeScript extension execution
- exact terminal renderer behavior
- every provider supported by PI
- package installation/update commands
- binary distribution

