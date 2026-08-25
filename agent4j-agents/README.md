# agent4j-agents

Reusable, policy-constrained agents built on `agent4j-coding`. This module
contains agent prompts and explicit tool registries; it does not depend on the
CLI or own sessions, provider selection, or terminal rendering.

## Build-triage agent

`BuildTriageAgent` diagnoses a Maven build failure. Its tool registry contains
`read`, `ls`, `grep`, `find`, and a fixed `run_maven_test` tool. The latter runs
only `mvn test` in the configured workspace; it accepts no model-supplied shell
arguments. The agent has no `write`, `edit`, or general `bash` capability.

Use it with a `CodingAgentRuntime` and an explicit trusted workspace:

```java
ToolRegistry tools = BuildTriageAgent.tools();
CodingAgentConfig config = CodingAgentConfig.builder(apiKey, model, workspace, sessionDirectory)
        .toolRegistry(tools)
        .build();
CodingAgentRuntime runtime = CodingAgentRuntime.create(config);
CodingAgentSession session = runtime.createSession(sessionFile, workspace);
PromptResult result = session.prompt(new PromptRequest(
        BuildTriageAgent.request(failureOutput),
        Optional.empty(), 6, 0, Optional.empty(), Optional.of("required"),
        null, Map.of(), List.of(), List.of(), null, null, Optional.empty(),
        Optional.of(BuildTriageAgent.systemPrompt())));
```

Maven plugins still run according to the workspace's existing configuration;
use the agent only for workspaces you trust. The expected output sections are
**Root cause**, **Evidence**, **Smallest viable fix**, and **Verification**.

## Java PR-review agent

`JavaPrReviewerAgent` reviews the current staged and unstaged tracked-file diff
relative to `HEAD`. Its tool registry contains `read`, `ls`, `grep`, `find`, and
a fixed `read_git_diff` tool that invokes:

```text
git diff --no-ext-diff --unified=80 HEAD --
```

It cannot write, edit, stage files, or execute a general shell command.
Untracked files are intentionally outside its initial scope. It reports only
actionable correctness, regression, security, concurrency, API-contract, and
missing-test findings in `[severity] file:line` form; style-only nits are out
of scope.

Use it with the same runtime/session pattern as build triage, substituting
`JavaPrReviewerAgent.tools()`, `JavaPrReviewerAgent.request(reviewContext)`,
and `JavaPrReviewerAgent.systemPrompt()`.

## Runnable examples

`agent4j-examples` supplies provider-backed launchers for both agents:

```bash
export AGENT4J_EXAMPLES_WORKSPACE="$PWD"

mvn -pl agent4j-examples -am test \
  -Dagent4j.liveOpenAiExamples=true \
  -Dagent4j.liveExample.mainClass=com.agent4j.examples.BuildTriageExample
```

Replace the final class with `com.agent4j.examples.JavaPrReviewerExample` to
run the reviewer. Both examples require the normal live-example provider
variables documented in `docs/examples/README.md`.
