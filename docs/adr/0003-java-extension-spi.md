# ADR 0003: Java-Only Extension SPI, First Slice

## Status

Accepted for Phase 13 slices 1 through 4.

## Context

PI's coding-agent extension system is a TypeScript-module API. Its current
surface includes `ExtensionAPI`, `registerTool`, `registerCommand`, and ordered
handlers for `session_start`, `before_agent_start`, `context`,
`before_provider_request`, `after_provider_response`, and `session_shutdown`.
The PI runner invokes extensions in loaded order and invokes handlers in their
registration order. Transforming handlers receive the result of the preceding
handler. Its published error policy logs ordinary extension-handler failures and
continues, while a failed `tool_call` handler blocks execution and a thrown tool
implementation error becomes an LLM-visible tool error.

This audit used PI's extension type definitions and runner on the `main` branch
at the time of this ADR: `packages/coding-agent/src/core/extensions/types.ts`
and `runner.ts` (runner blob `fc486e4cd4d1768f2e8b34e6fbd0627e72bc40c7`).

## Decision

Expose the initial Java contracts in `com.agent4j.coding.extension`:

| PI concept | Java first-slice contract | Notes |
| --- | --- | --- |
| extension factory / `ExtensionAPI` | `CodingAgentExtension` + `CodingExtensionRegistrar` | Java objects are passed explicitly by host code. |
| `registerTool` | `registerTool(ToolSpec, Tool)` | Reuses the provider-neutral core tool abstractions. |
| `registerCommand` | `CodingExtensionCommand` | Command definition is coding-owned and terminal-independent. |
| `session_start` / `session_shutdown` | `onSessionStart` / `onSessionShutdown` | The runtime integration comes in a later slice. |
| `before_agent_start` | `beforeAgentStart` | The event can replace prompt/system-prompt values for the next extension. |
| `context` | `transformContext` | Each extension receives the preceding returned message list. |
| `before_provider_request` | `beforeProviderRequest` | Each extension receives the preceding request. |
| `after_provider_response` | `afterProviderResponse` | Exposes status and headers before stream consumption. |

`agent4j-core` remains provider- and extension-agnostic. The SPI belongs in
`agent4j-coding`, which may depend on core transcript/tool types and AI request
types at the outer coding-agent boundary.

### Extension model and contribution registry

Slice 2 adds `AgentExtension`, `ExtensionContext`, and
`ExtensionContributionRegistry`. Host code supplies an ordered list of Java
`AgentExtension` objects and calls `resolve(...)`; there is still no discovery
or loading step. The resolved `ResolvedExtensionContributions` contains typed,
immutable contributions for tools, tool-execution hooks, commands, and session
lifecycle listeners. Each contribution records its owning extension.

The resolver preserves the supplied extension order and the order in which an
extension registers each contribution. Extension, tool, hook, command, and
lifecycle-listener names each have their own namespace. A duplicate name is
rejected at its second registration with a deterministic configuration error.

### Bootstrap and discovery

Slice 3 adds `ExtensionLoader`. It combines trusted extensions supplied through
its builder with `ServiceLoader` providers visible to the application class
loader, preserving programmatic registrations before discovered providers. It
does not inspect the project directory, install packages, execute TypeScript,
or load code from any project-controlled location.

An explicitly disabled loader returns no extensions. No service descriptor is
valid and leaves programmatic extensions available. A duplicate extension name
or an invalid service provider fails startup deterministically rather than
silently selecting a provider.

### Tool contribution and hook wiring

Slice 4 resolves extensions once when `CodingAgentRuntime` is created, merging
their tools with the runtime registry while rejecting replacement of an existing
tool. All sessions reuse the resulting immutable registry and contributed hook
list. Contributed `ToolExecutionHook` instances are passed directly to
`AgentLoop`, so the core timing remains start event, before hooks, execution or
blocked result, after hooks, end event, then transcript persistence. Hooks can
add per-call attributes through `ToolContext.putAttribute(...)`; the executing
tool and later hooks can read those values. No extension code is loaded from the
project workspace.

### Agent-start and context-transform hooks

Slice 5 adds ordered agent-start and context-transform contributions. Agent-start
hooks can replace the prompt and system prompt before the turn starts.
Context-transform hooks receive and replace only the in-memory model input;
`CodingAgentSession` keeps the original prompt in `promptMessages`, which is
the value persisted to JSONL. Consequently, extensions cannot silently rewrite
stored session history through this hook surface.

For both hook types, each returned value is passed to the next hook in
registration order. An ordinary hook failure is logged, its transformation is
discarded, and later hooks continue using the last successful value.

### Ordering and duplicate names

The eventual runtime accepts an explicit ordered extension list. It validates
extension names before registration and rejects duplicate extension names.
Extensions register and lifecycle methods execute in that supplied order; if one
extension registers multiple handlers in a future event-specific API, those run
in registration order. Transform results chain left-to-right.

Tool and command names occupy separate namespaces. Duplicate contributed names
are configuration errors, including duplicates with built-ins. Built-in tool
replacement needs a separate explicit override API and is not part of this
slice; silently taking the last registration would make ordering a security
decision.

### Lifecycle and exceptions

For a created or resumed session the intended order is:

1. validate extension identities and collect registrations;
2. construct the session;
3. call `onSessionStart`;
4. for each prompt, chain `beforeAgentStart`, then `transformContext` before
   every model request, then `beforeProviderRequest`, then
   `afterProviderResponse` before stream consumption;
5. call `onSessionShutdown` before close or session replacement.

The later dispatcher must isolate ordinary lifecycle failures: report an
extension diagnostic, skip the failed handler's transformation, and continue
with the current value. Failures from an extension-provided `Tool` retain the
existing core tool failure semantics. Before a PI-equivalent `tool_call` gate is
implemented, no extension lifecycle method can block a tool.

## First-release boundary

This slice deliberately provides contracts only. It has no `ServiceLoader`, no
classpath scanning, no project-local discovery, no TypeScript execution, no
Node process, no package installation, and no dynamic loading of project code.
Applications will eventually opt in by constructing Java extension instances in
their own trusted code. Project trust remains represented by
`CodingExtensionContext.projectTrusted()` for the later trusted-resource policy.

## Consequences

The small API permits the runtime/CLI integration to be added without moving
extension concerns into `agent4j-core`. It intentionally does not yet claim
support for PI UI, renderer, model-provider registration, resource-discovery,
session mutation, cancellation, command dispatch, dynamic tool activation, or
the PI package format.
