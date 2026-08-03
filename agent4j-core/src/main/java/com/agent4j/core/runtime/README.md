# Runtime Message Lists

`AgentLoop` keeps several message lists because each list has a different
runtime contract. They should not be collapsed into one list.

## `modelMessages`

`modelMessages` is the actual LLM input for the next model call.

Example shape:

```text
system prompt
user message
assistant tool call
tool result
compaction summary
retained tail
...
```

It is provider-facing and uses `AiMessage`, converted from `AgentMessage`.

When compaction happens, `modelMessages` is rebuilt because the model should no
longer see the full old transcript. It should see:

```text
system + compactionSummary + retainedTail
```

`modelMessages` is not the durable transcript. It is the current model request
payload.

## `transcriptMessages`

`transcriptMessages` is the current model-visible conversation transcript in
`AgentMessage` form.

It starts as:

```java
new ArrayList<>(request.messages())
```

Then the loop appends:

- assistant responses
- tool result messages
- queued steering/follow-up messages

When threshold compaction happens, `transcriptMessages` is rewritten:

```text
old full transcript
=> compaction summary + retained tail
```

This is the mutable working transcript used to rebuild `modelMessages`.

## `newMessages`

`newMessages` is what the current `runTurn` invocation generated or accepted as
new transcript entries. It is returned through `AgentLoopResult.messages()` and
emitted in `AgentEnded`.

It starts with:

```java
new ArrayList<>(request.promptMessages())
```

Then the loop appends:

- assistant messages
- tool result messages
- queued messages
- compaction summary messages

Important distinction:

```text
transcriptMessages = full working model-visible state
newMessages        = only new messages from this run
```

Callers should persist `newMessages`, not `transcriptMessages`.

## `assistantMessages`

`assistantMessages` is a narrower result list containing only assistant model
messages produced by the LLM.

It includes:

- assistant text replies
- assistant tool-call messages

It does not include:

- user prompt messages
- tool result messages
- compaction summaries
- queued user messages

This exists because callers often need "what did the assistant say/do?"
separately from the full generated transcript.

## Example

Initial session:

```text
user-1: "Read README.md and summarize it"
assistant-1: toolCall(read)
tool-result-1: README content
assistant-2: summary
```

New user turn:

```text
user-2: "Make it shorter"
```

At start:

```text
transcriptMessages:
  user-1
  assistant-1
  tool-result-1
  assistant-2
  user-2

modelMessages:
  converted transcriptMessages, plus system prompt if present

newMessages:
  user-2

assistantMessages:
  empty
```

If threshold compaction happens:

```text
transcriptMessages:
  compaction-summary-1
  user-2

modelMessages:
  system
  compaction-summary-1
  user-2

newMessages:
  user-2
  compaction-summary-1

assistantMessages:
  empty
```

Then the model replies:

```text
assistant-3: shorter summary
```

After reply:

```text
transcriptMessages:
  compaction-summary-1
  user-2
  assistant-3

modelMessages:
  system
  compaction-summary-1
  user-2
  assistant-3

newMessages:
  user-2
  compaction-summary-1
  assistant-3

assistantMessages:
  assistant-3
```

Short version:

```text
modelMessages      = current LLM input
transcriptMessages = current working transcript in AgentMessage form
newMessages        = messages produced/accepted during this run for persistence
assistantMessages  = assistant-only model outputs
```
