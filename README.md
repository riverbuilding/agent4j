# agent4j

agent4j is an idiomatic Java 21 implementation of the [PI coding-agent
harness](https://github.com/earendil-works/pi). It preserves PI's observable
runtime concepts—streaming model calls, tool use, JSONL sessions, steering,
follow-up, compaction, and CLI modes—rather than translating PI's TypeScript
internals line for line.

The project is a multi-module Maven build and is currently suitable for
developing and exercising a small coding agent. It has a tested line-oriented
interactive shell; it is not yet a full visual replacement for PI's TUI.

Visit the [agent4j website](https://agent4j-7mv9.vercel.app/).

## What works today

- OpenAI Responses and Anthropic Messages providers, plus configured
  OpenAI-Responses-compatible providers such as OpenRouter.
- Persisted PI-shaped JSONL sessions, branching, retries, compaction, abort,
  live steering, and follow-up queues.
- Built-in coding tools: `read`, `write`, `edit`, `bash`, `ls`, `grep`, and
  `find`.
- Print, JSON-event, RPC, and interactive CLI modes.
- PI-style resource discovery for `AGENTS.md`, `CLAUDE.md`, `.pi` resources,
  system prompts, prompt templates, skills, themes, and settings.
- A Java extension SPI for tools, hooks, lifecycle listeners, and interactive
  commands.

## Modules

| Module | Responsibility |
| --- | --- |
| `agent4j-ai` | Provider-neutral model API, authentication, and OpenAI/Anthropic streaming adapters. |
| `agent4j-core` | Agent loop, messages, events, tool execution, retries, and queues. |
| `agent4j-coding` | Coding runtime, JSONL sessions, compaction, resources, extensions, and coding tools. |
| `agent4j-agents` | Reusable policy-constrained agents, including Maven build triage and Java PR review. |
| `agent4j-cli` | Picocli entry point and print, JSON, RPC, and interactive hosts. |
| `agent4j-testkit` | Fake providers, recorded fixtures, and shared contract-test support. |
| `agent4j-examples` | Opt-in, provider-backed examples. |

## Requirements

- JDK 21
- Maven 3.9 or newer
- An API key for the provider you intend to use

## Build and test

Run the full test suite from the repository root:

```bash
mvn test
```

Run just the CLI module and its required dependencies:

```bash
mvn -pl agent4j-cli -am test
```

## Run the interactive CLI

The CLI is launched from Maven during development. First install the current
reactor modules into the local Maven repository; this makes a separate
`exec:java` invocation resolve the current sibling-module classes.

```bash
mvn -q -pl agent4j-cli -am install -DskipTests
```

Set the agent4j-scoped provider variables. The following configuration uses
OpenRouter's OpenAI Responses-compatible endpoint:

```bash
export AGENT4J_API_KEY='your-openrouter-api-key'
export AGENT4J_BASE_URL='https://openrouter.ai/api/v1'
export AGENT4J_MODEL='openai/gpt-4.1'
```

Start an interactive session in the current directory:

```bash
mvn -q -pl agent4j-cli exec:java \
  -Dexec.mainClass=com.agent4j.cli.Agent4jCli
```

At the `agent4j>` prompt, ask for a task such as:

```text
Summarize this repository's architecture for a new contributor. Inspect the root pom,
module poms, and relevant source/docs; cite the files you used.
```

Use `/help` to list interactive commands and `/exit` to leave the session.
`--model` overrides `AGENT4J_MODEL`; a discovered `.pi/settings.json`
`defaultModel` takes precedence over `AGENT4J_MODEL` but not `--model`.

For native Anthropic use, set `ANTHROPIC_API_KEY` and select a model with
`--provider anthropic --model <model-id>`. Run `--list-models` to display the
configured catalog.

## Configuration and resources

agent4j discovers global resources in `~/.pi/agent` and project resources in
`<workspace>/.pi`. Important files include:

- `.pi/settings.json` for `defaultProvider`, `defaultModel`, and provider/model
  definitions.
- `.pi/models.json` for additional models and OpenAI-compatible providers.
- `AGENTS.md` (and `CLAUDE.md` fallback), `SYSTEM.md`, and `APPEND_SYSTEM.md`
  for model context.
- `.pi/prompts`, `.pi/skills`, and `.pi/themes` for prompt templates, skills,
  and themes.

See [resource and settings parity](docs/resource-settings-parity.md) for the
supported settings and precedence rules. `--append-system-prompt` adds a
session-wide instruction while retaining the built-in coding prompt;
`--system-prompt` replaces that baseline.

## Current scope and known gaps

agent4j targets PI coding-agent behavior around version `0.82.x`. The runtime
and basic interactive contract are covered by deterministic fake-provider
tests, but full PI parity is not claimed.

Notable remaining gaps are rich/full-screen TUI behavior, editor history and
completion, Alt+Enter key handling, transcript replay, themes in the terminal
UI, interactive trust/settings dialogs, PI package/TypeScript extension
compatibility, broad provider-catalog parity, and complete production-login
verification. The interactive shell is deliberately a reliable line interface
before those richer UI layers are added.

For the authoritative compatibility detail, see:

- [PI compatibility contract](docs/pi-compatibility.md)
- [interactive shape audit](docs/pi-interactive-shape-audit.md)
- [CLI shape audit](docs/pi-cli-shape-audit.md)
- [simple-project coding-agent parity audit](docs/pi-mini-coding-agent-parity-audit.md)
- [implementation plan](docs/implementation-plan.md)

## Development notes

Keep changes small and test the narrowest affected module first. The project
uses fake-provider and recorded-fixture tests for deterministic behavior;
provider-backed smoke tests are opt-in and require credentials. See
[the OpenAI SDK guide](docs/openai-sdk-guide.md) for subscription-login and
credential-lifecycle details.
