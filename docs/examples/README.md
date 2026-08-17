# Live OpenAI Examples

Phase 12 examples use agent4j's production OpenAI Responses runtime. They are
opt-in, may incur API charges, and do not run as part of normal `mvn test`.
The current foundation provides a preflight check only; it validates local setup
and creates then cleans temporary paths without sending an API request.

## Setup

Create an API key in the OpenAI Platform and export it only in your shell. Do
not pass a key on the command line, add it to a settings file, or commit it.

```bash
export AGENT4J_API_KEY="..."
export AGENT4J_MODEL="<enabled-model-id>"
```

To use an OpenAI Responses-compatible provider, retain the provider API key in
`AGENT4J_API_KEY`, select its model identifier, and set its API base URL. For
example, OpenRouter's free-model router is configured as follows:

```bash
export AGENT4J_API_KEY="<your-openrouter-api-key>"
export AGENT4J_BASE_URL="https://openrouter.ai/api/v1"
export AGENT4J_MODEL="openai/openrouter/free"
export AGENT4J_SWITCH_MODEL="openai/meta-llama/llama-3.2-3b-instruct:free"
```

The live runtime keeps this credential and base URL only in memory for the
example process; it does not write either to the user credential store. The
configured URL must expose the OpenAI Responses endpoint at `/responses`.

`AGENT4J_SWITCH_MODEL` is optional except for the model-switching walkthroughs.
It must use `provider/model` form; for OpenRouter-compatible calls the provider
is `openai` and the remainder is OpenRouter's exact model ID. Choose models
currently available to your account, including `:free` variants when using an
OpenRouter free-tier key.

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

## Progressive live walkthroughs

Each command below sends a real streamed request and may incur provider
charges. They reuse the environment configuration above and create temporary
workspaces and sessions unless you explicitly select their locations.

### 01-real-prompt

Sends one short prompt, prints assistant text as it arrives, then reports the
usage returned by the provider.

```bash
mvn -pl agent4j-examples -am test \
  -Dagent4j.liveOpenAiExamples=true \
  -Dagent4j.liveExample.mainClass=com.agent4j.examples.RealPromptExample
```

### 02-streaming-events

Builds on the first walkthrough by printing the public `AgentEvent` lifecycle
boundaries around a real streamed response.

```bash
mvn -pl agent4j-examples -am test \
  -Dagent4j.liveOpenAiExamples=true \
  -Dagent4j.liveExample.mainClass=com.agent4j.examples.StreamingEventsExample
```

### 03-tool-calling

Builds on streaming by exposing exactly one `workspace_status` tool. It only
returns the session workspace path; it cannot read, write, delete, or execute
anything. The walkthrough fails clearly if the selected model does not invoke
the tool, so choose a model that supports function calling.

```bash
mvn -pl agent4j-examples -am test \
  -Dagent4j.liveOpenAiExamples=true \
  -Dagent4j.liveExample.mainClass=com.agent4j.examples.ToolCallingExample
```

### 04-persistent-sessions

Creates a JSONL session, sends one real prompt, then releases that first session
handle. It resumes the JSONL into a new `CodingAgentSession`, reports its
persisted entry and restored-message counts, and sends a follow-up question
that relies on the first turn's conversation history. Session writes are
durable at turn completion, so there is no file handle to close between the
initial and resumed sessions.

```bash
mvn -pl agent4j-examples -am test \
  -Dagent4j.liveOpenAiExamples=true \
  -Dagent4j.liveExample.mainClass=com.agent4j.examples.PersistentSessionsExample
```

### 05-live-session-control

Runs three real streamed prompts in one persisted session. It pauses for one
terminal command during each active stream: `/steer <text>`, `/follow-up <text>`,
and `/abort`. The first two commands are consumed in a subsequent model turn;
the last produces the public aborted event and ends the active prompt locally.

Run this from an interactive terminal. As soon as streamed text appears for a
stage, enter the command it displays and press Enter. The model can finish a
short response before a command is entered; if that happens, the walkthrough
reports that completed response and automatically restarts the stage before
applying your already-entered command. Cancellation stops the local streamed
session at the next received provider event, so a small amount of
already-buffered text may still be printed.

```bash
mvn -pl agent4j-examples -am test \
  -Dagent4j.liveOpenAiExamples=true \
  -Dagent4j.liveExample.mainClass=com.agent4j.examples.LiveSessionControlExample
```

### 06-resources-and-coding-tools

Creates a disposable `Lantern Library` workspace containing `README.md`, a
source file, `AGENTS.md`, project `.pi` settings, and system-prompt resources.
It discovers those resources from a temporary example home and workspace,
prints the discovered settings/context files, builds a request-scoped system
prompt, and sends one real prompt.

Only the built-in `read`, `ls`, `grep`, and `find` tools are registered. The
example asks the model to read `README.md`; the coding-tool path policy rejects
paths outside the workspace, and no write, edit, or shell tool is available.
The sample files live in a newly created child directory, so an explicitly
configured workspace is never overwritten. They are deleted with the temporary
workspace unless you set an explicit workspace path.

```bash
mvn -pl agent4j-examples -am test \
  -Dagent4j.liveOpenAiExamples=true \
  -Dagent4j.liveExample.mainClass=com.agent4j.examples.ResourcesAndCodingToolsExample
```

### 07-model-switching

Uses one persisted session and an application-owned selected-model value. The
first turn uses the initially selected model; the example then changes that
value and sends the second turn with the configured switch model. A model is
selected when a `PromptRequest` begins, so the change affects the next turn,
not a request that is already streaming.

```bash
mvn -pl agent4j-examples -am test \
  -Dagent4j.liveOpenAiExamples=true \
  -Dagent4j.liveExample.mainClass=com.agent4j.examples.ModelSwitchingExample
```

### 08-prompt-model-override

Sends two consecutive `PromptRequest`s through one persisted session. The first
relies on the runtime/provider default; the second supplies
`PromptRequest.model` with `AGENT4J_SWITCH_MODEL`. The second turn retains the
first turn's conversation history while using its own model selection.

```bash
mvn -pl agent4j-examples -am test \
  -Dagent4j.liveOpenAiExamples=true \
  -Dagent4j.liveExample.mainClass=com.agent4j.examples.PromptModelOverrideExample
```

### 09-compaction-and-branching

Creates two turns in one JSONL session, runs manual compaction with a small
retained tail, and prints the summary plus estimated before/after context
tokens. It then forks from the first turn's active entry, showing that the fork
contains only that selected path, while the original session is resumed from
its latest compacted path and continued with one final prompt.

Manual compaction sends a separate provider request whose input includes the
history selected for summarization. This walkthrough makes four provider
requests: two setup turns, one summary, and one resumed turn. The summary's
input grows with the compacted history, so inspect the printed token counts and
use a low-cost model before trying it on a large session.

The original and forked JSONL paths are printed. Temporary paths are cleaned on
normal exit; set `AGENT4J_EXAMPLES_SESSION_DIRECTORY` to retain them for
inspection, or remove interrupted-run paths manually.

```bash
mvn -pl agent4j-examples -am test \
  -Dagent4j.liveOpenAiExamples=true \
  -Dagent4j.liveExample.mainClass=com.agent4j.examples.CompactionAndBranchingExample
```

### 10-cli-modes

Runs composed Java calls to the actual CLI command boundary: print mode, JSON
event mode, JSONL RPC mode, session resume, and session fork. The example
passes its existing in-memory live-example credential and optional base URL to
each CLI request with `--api-key` and `--base-url`; it does not require a
second set of provider environment variables.

For OpenRouter, retain the `openai/` provider prefix in `AGENT4J_MODEL`, for
example `openai/openrouter/free`. The Java example composes each argument list
with `--no-tools`, `--model`, and an explicit `--session-dir`, so it has no
workspace side effects. Its temporary session files are cleaned on normal exit;
set `AGENT4J_EXAMPLES_SESSION_DIRECTORY` to retain and inspect them.

```bash
mvn -pl agent4j-examples -am test \
  -Dagent4j.liveOpenAiExamples=true \
  -Dagent4j.liveExample.mainClass=com.agent4j.examples.CliModesExample
```

## Bounds and cost

The walkthroughs pass these defaults from `LiveExampleConfiguration` to
`CodingAgentRuntime`:

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
The resources-and-coding-tools walkthrough adds only workspace-scoped,
read-only filesystem tools. Future tool walkthroughs must keep their default
tool sets constrained to the example workspace and document any side effects
before they run.
