# Tool Result Contract

Phase 3 tool results are JSON values carried by `ToolResult.content`.

Common `ToolResult` fields:

- `toolCallId`: original tool call id
- `toolName`: executed tool name
- `error`: `true` for tool-level errors, `false` for successful tool execution
- `content`: tool-specific JSON payload, or an error string
- `metadata`: optional diagnostic object

Thrown tool exceptions are converted by `ToolExecutor` into:

```json
{
  "error": true,
  "content": "error message",
  "metadata": {
    "message": "error message",
    "exceptionClass": "java.lang.Exception"
  }
}
```

Tool-handled validation errors use the same shape without `exceptionClass`.

## read

Arguments:

- `path`: workspace-relative file path

Success content:

```json
{
  "path": "/absolute/path",
  "content": "file text",
  "truncated": false,
  "originalLength": 123
}
```

Binary files are rejected.

## write

Arguments:

- `path`
- `content`

Success content:

```json
{
  "path": "/absolute/path",
  "bytesWritten": 123
}
```

## edit

Arguments:

- `path`
- `oldText`
- `newText`

Success content:

```json
{
  "path": "/absolute/path",
  "replacements": 1,
  "matchCount": 1,
  "ambiguous": false,
  "oldText": "before",
  "newText": "after",
  "diff": "- before\n+ after",
  "contextBefore": "",
  "contextAfter": ""
}
```

Only the first exact occurrence is replaced. Binary files are rejected.

## bash

Arguments:

- `command`
- optional `timeoutSeconds`

Success content:

```json
{
  "command": "pwd",
  "exitCode": 0,
  "stdout": "...",
  "stderr": "...",
  "stdoutTruncated": false,
  "stderrTruncated": false,
  "timedOut": false,
  "durationMillis": 12
}
```

Nonzero exits are represented as successful bash results with nonzero
`exitCode`. Timeouts return `exitCode: -1` and `timedOut: true`.

## ls

Arguments:

- optional `path`, default `.`

Success content:

```json
{
  "path": "/absolute/path",
  "entries": [
    {"path": "relative/path", "directory": false}
  ],
  "truncated": false,
  "totalEntries": 1
}
```

## grep

Arguments:

- `pattern`: Java regular expression
- optional `path`, default `.`

Success content:

```json
{
  "pattern": "target",
  "matches": [
    {"path": "relative/path", "line": 1, "text": "matching line"}
  ],
  "truncated": false,
  "totalMatches": 1
}
```

Binary files are skipped.

## find

Arguments:

- optional `path`, default `.`
- optional `name`, substring match against file name

Success content:

```json
{
  "path": "/absolute/path",
  "name": ".java",
  "entries": [
    {"path": "relative/path", "directory": false}
  ],
  "truncated": false,
  "totalEntries": 1
}
```

