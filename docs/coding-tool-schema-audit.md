# Coding Tool Schema Audit

Phase 8 pins the Java built-in coding tool boundary to a stable, PI-compatible
baseline.

## Tool Input Schemas

All built-in tool schemas are JSON objects with `additionalProperties: false`.
Each schema exposes only the arguments accepted by that tool:

- `read`: required `path`
- `write`: required `path`, `content`
- `edit`: required `path`, `oldText`, `newText`
- `bash`: required `command`, optional `timeoutSeconds`
- `ls`: optional `path`
- `grep`: required `pattern`, optional `path`
- `find`: optional `path`, `name`

Each property carries a description so providers receive the same argument
contract the Java executor enforces.

## Result Shape

Result payloads are JSON objects for successful calls and text error payloads
for failed calls. Successful file/path results use workspace-relative `path`
values, not host-absolute paths.

Current result keys:

- `read`: `path`, `content`, `truncated`, `originalLength`
- `write`: `path`, `bytesWritten`
- `edit`: `path`, `replacements`, `matchCount`, `ambiguous`, `oldText`,
  `newText`, `diff`, `contextBefore`, `contextAfter`
- `bash`: `command`, `exitCode`, `stdout`, `stderr`, `stdoutTruncated`,
  `stderrTruncated`, `timedOut`, `durationMillis`
- `ls`: `path`, `entries`, `truncated`, `totalEntries`
- `grep`: `pattern`, `path`, `matches`, `truncated`, `totalMatches`
- `find`: `path`, `name`, `entries`, `truncated`, `totalEntries`

## Remaining PI Audit

This slice closes schema leakage and result-path normalization. Remaining
PI-source-specific audit work is exact tool naming/description text, image read
payload shape, edit multi-replacement behavior, and streaming render/update
event details.
