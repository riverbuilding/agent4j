# Runtime Conversation Context

`AgentLoop` keeps conversation state in `AgentConversationContext`.

This is closer to PI's agent loop shape: one mutable conversation context owns
the model-visible transcript, and provider-facing model input is rebuilt at the
LLM boundary.

## Canonical Transcript

`AgentConversationContext.transcriptMessages()` is the current model-visible
conversation in `AgentMessage` form.

It starts from `AgentLoopRequest.messages()` and then accepts:

- assistant responses
- tool result messages
- queued steering/follow-up messages

When compaction happens, the context replaces this transcript with:

```text
compaction summary + retained tail
```

The old full transcript is no longer part of the model-visible working state.

## Generated Messages

`AgentConversationContext.generatedMessages()` is the subset produced or
accepted during the current `runTurn` invocation.

It is returned through `AgentLoopResult.messages()` and emitted in
`AgentEnded`.

It starts from `AgentLoopRequest.promptMessages()` and then records:

- queued steering/follow-up messages accepted during this run
- assistant messages
- tool result messages
- compaction summary messages

Callers should persist generated messages, not the whole working transcript.

## Model Input

`AgentConversationContext.toModelMessages(...)` converts the current transcript
to provider-facing `AiMessage` values and prepends the system prompt when
present.

`AgentLoop` does not maintain a long-lived `modelMessages` list. Each model
round asks the context to rebuild model input from the current transcript. This
keeps the model boundary explicit and prevents stale model input after queue
drain, tool results, or compaction.

## Assistant Results

`AgentLoopResult.assistantMessages()` remains a result accumulator containing
only assistant model messages produced by the LLM.

It is intentionally separate from the conversation context because it is output
reporting, not canonical conversation state.
