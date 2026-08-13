# agent4j Project Instructions

- When waiting for Codex subagents with `wait_agent`, pass `timeout_ms: 300000` unless the user explicitly requests a different wait timeout.

## Project Overview

agent4j is a Java port of the PI coding agent harness.

## Repository Structure

- `agent4j-ai/` — AI and OpenAI SDK integration
- `agent4j-core/` — core runtime and session functionality
- `agent4j-coding/` — coding-agent capabilities
- `agent4j-cli/` — command-line interface
- `agent4j-testkit/` — shared testing utilities
- `docs/` — architecture decisions, compatibility notes, and implementation documentation

The project is a multi-module Maven build managed by the root `pom.xml`.

## Build and Test Commands

Run the complete test suite from the repository root:

```bash
mvn test
```

Build or test an individual module with Maven's `-pl` option. Include `-am` when required module dependencies must also be built.

## Java and Maven Conventions

- Use Java 21 features and APIs where appropriate.
- Follow the existing package, naming, and class organization conventions in the module being changed.
- Use the Maven wrapper or the repository's configured Maven version when one is provided.
- Keep module-specific dependencies in the module's `pom.xml`; keep shared dependency versions in the root `pom.xml` when appropriate.

## Code Quality

Prefer precise, intention-revealing names for variables, parameters, and methods.

Naming guidelines:

- Name variables after the abstraction/type or their responsibility, not merely their contents.
- Prefer `registry` over `commands` for an `InteractiveCommandRegistry`.
- Prefer `executor` over `tasks` for a task executor.
- Prefer `repository` over `users` for a `UserRepository`.
- Prefer `request` over `data` for a request object.
- Avoid vague names such as `data`, `obj`, `result`, `manager`, `helper`, `util`, or `thing`
  unless the name accurately represents the abstraction.
- Local variable names should make the role of the object obvious without requiring the
  reader to inspect its type.

When generating or modifying code, review newly introduced names for semantic precision
before finishing.

## Testing Guidelines

- Add or update tests for behavior changes and new functionality.
- Keep tests in the module's existing test source layout.
- Use the testing conventions and utilities already established in the affected module.
- Run the narrowest relevant test command during iteration, then run `mvn test` before finishing when practical.

## Generated Code

There is no repository-wide generated-code workflow currently documented. If generated sources are introduced, document their generator and regeneration command before relying on them, and avoid hand-editing generated output.

## Code modification policy

Optimize for reviewability and minimal diffs.

When editing existing code:

1. Preserve existing formatting, whitespace, line wrapping, and argument layout.
2. Do not make cosmetic changes to lines unrelated to the requested behavior.
3. Do not perform opportunistic refactoring or cleanup.
4. Do not rename variables, methods, or classes unless required by the task.
5. Do not reorder methods, imports, fields, or declarations unless required.
6. Do not run broad auto-formatters unless explicitly requested.
7. Prefer modifying the smallest possible contiguous region of code.
8. Before finishing, inspect `git diff` and remove unrelated changes.

A line should normally appear in the diff only when changing it is necessary
to satisfy the request.
