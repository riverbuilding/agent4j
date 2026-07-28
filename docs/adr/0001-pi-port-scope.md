# ADR 0001: PI Harness Port Scope

## Status

Accepted

## Context

agent4j is a greenfield Java port of the PI coding agent harness. PI is a
TypeScript monorepo with three conceptual layers:

- a provider-neutral LLM streaming API
- a tool-calling agent runtime
- a coding-agent harness with sessions, settings, tools, resources, CLI modes,
  and terminal UI

The goal is behavioral compatibility for the harness contract, not a
line-by-line translation of TypeScript internals.

## Decision

agent4j will be implemented as an idiomatic Java 21 multi-module project with
compatibility focused on externally observable behavior:

- session JSONL format and tree semantics
- message and event model
- built-in coding tools
- prompt, steer, follow-up, abort, retry, and compaction behavior
- print, JSON, RPC, and eventually interactive modes
- resource discovery for settings, context files, skills, prompts, and themes

The first implementation slice will build the session model and JSONL parser.
Provider adapters, compaction, and terminal UI will follow after the core
runtime is testable with fake model streams.

## Consequences

The port can keep Java code cohesive and testable while preserving PI-compatible
artifacts. Native TypeScript extension execution is not part of the initial
scope. agent4j will expose a Java extension SPI first; a Node or RPC bridge can
be added later if PI package compatibility becomes a hard requirement.

## Module Shape

- `agent4j-ai`: model/provider abstraction and streaming normalization
- `agent4j-core`: agent loop, messages, tools, state, retries, queueing
- `agent4j-coding`: sessions, settings, resources, compaction, coding tools
- `agent4j-cli`: process entrypoints for print, JSON, RPC, and interactive modes
- `agent4j-testkit`: fixtures, fake providers, and parity utilities

