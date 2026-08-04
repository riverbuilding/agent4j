# Custom Message Prompt Audit

Phase 8 pins the Java coding-agent custom/session message prompt boundary.

## Conversion Boundary

`CodingAgentMessageConverter` is the coding-layer `convertToLlm` boundary. It
delegates normal `user`, `assistant`, `toolResult`, and `system` messages to the
core converter, and renders coding/session-only messages as user context.

## Prompt Wrappers

Current coding custom messages render as:

```text
<bashExecution>
Command: ...
Exit code: ...
Output:
...
</bashExecution>
```

```text
<branchSummary>
...
</branchSummary>
```

```text
<compactionSummary>
...
</compactionSummary>
```

```text
<customMessage type="...">
...
</customMessage>
```

The `customMessage` `type` attribute is escaped for XML-like prompt markup.
Unknown custom roles are skipped by the coding converter unless a later PI
source audit identifies a concrete rendering contract for them.

## Remaining PI Audit

This slice pins the Java prompt contract and closes unsafe attribute rendering.
Remaining PI-source-specific work is exact prompt text comparison against
`packages/coding-agent/src/core/messages.ts` and any additional concrete custom
message variants not yet represented in Java.
