# Live OpenAI Examples

Phase 12 examples use agent4j's production OpenAI Responses runtime. They are
opt-in, may incur API charges, and do not run as part of normal `mvn test`.
The current foundation provides a preflight check only; it validates local setup
and creates then cleans temporary paths without sending an API request.

## Setup

Create an API key in the OpenAI Platform and export it only in your shell. Do
not pass a key on the command line, add it to a settings file, or commit it.

```bash
export OPENAI_API_KEY="..."
export AGENT4J_OPENAI_MODEL="<enabled-model-id>"
```

To use an OpenAI Responses-compatible provider, retain the provider API key in
`OPENAI_API_KEY`, select its model identifier, and set its API base URL. For
example, OpenRouter's free-model router is configured as follows:

```bash
export OPENAI_API_KEY="<your-openrouter-api-key>"
export OPENAI_BASE_URL="https://openrouter.ai/api/v1"
export AGENT4J_OPENAI_MODEL="openrouter/free"
```

The live runtime keeps this credential and base URL only in memory for the
example process; it does not write either to the user credential store. The
configured URL must expose the OpenAI Responses endpoint at `/responses`.

Choose a model enabled for your account. The [official OpenAI model
guidance](https://developers.openai.com/api/docs/guides/latest-model) recommends
the Responses API for reasoning, tool-calling, and multi-turn workflows;
agent4j's examples use that provider boundary. Select a lower-cost model
appropriate for the walkthrough before running it, and consult current [OpenAI
model pricing](https://developers.openai.com/api/docs/models) for the actual
rates available to your account.

## Run the preflight check

```bash
mvn -pl agent4j-examples -am test -Dagent4j.liveOpenAiExamples=true
```

The command reports the selected model, bounded output/tool limits, and the
workspace/session locations. It deliberately does not print the API key and
does not send a request.

## Bounds and cost

Future walkthroughs inherit these defaults from `LiveExampleRuntime`:

- maximum output tokens: `256`
- maximum tool rounds: `1`

Override them only when a walkthrough explicitly needs more capacity:

```bash
export AGENT4J_EXAMPLES_MAX_OUTPUT_TOKENS=256
export AGENT4J_EXAMPLES_MAX_TOOL_ROUNDS=1
```

The approximate request charge is the selected model's input-token rate times
actual input tokens, plus its output-token rate times actual output tokens.
The limits reduce exposure but do not guarantee a fixed price, because input,
reasoning, and tool-related usage vary by model and prompt.

## Workspace and session cleanup

By default, the foundation creates separate operating-system temporary
directories for the example workspace and session files. A walkthrough uses
try-with-resources and deletes only the temporary directories it created when
it exits normally. If the process is interrupted, the printed paths identify
what you can inspect and remove manually.

Set either variable only when you want to retain artifacts. Explicitly chosen
directories are never deleted automatically:

```bash
export AGENT4J_EXAMPLES_WORKSPACE="/private/path/to/example-workspace"
export AGENT4J_EXAMPLES_SESSION_DIRECTORY="/private/path/to/example-sessions"
```

The foundation does not register filesystem-writing or process-executing tools.
Future tool walkthroughs must keep their default tool sets constrained to the
example workspace and document any side effects before they run.
