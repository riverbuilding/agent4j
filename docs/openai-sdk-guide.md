# OpenAI SDK Guide

This guide shows the current Java SDK boundary for OpenAI API-key and ChatGPT
subscription use. The OAuth/PKCE details are in
[`openai-subscription-login-flow.md`](openai-subscription-login-flow.md).

## Setup

Add `agent4j-coding` and construct the coding runtime around a model that your
account can use:

```java
AiModelReference model = new AiModelReference("openai", "<enabled-model-id>");

CodingAgentRuntimeServices services = CodingAgentRuntimeServices.withOpenAi(
        OpenAiCodingRuntimeOptions.builder(model).build());
AgentSessionRuntime runtime = new CodingAgentSessionRuntime(services);
LoginService login = runtime.loginService();
```

The default credential store is user-scoped at `~/.pi/agent/auth.json`. It is
not written into the project or session JSONL. Provide a custom
`AuthCredentialStore` through `OpenAiCodingRuntimeOptions.builder(model)` when
an application needs a different private storage location.

## Browser Login

Browser subscription login is one call:

```java
AuthStatus status = login.loginOpenAiSubscription();
```

It binds `http://localhost:1455/auth/callback`, opens the system browser, waits
for the OAuth callback, persists the resulting subscription credential, and
returns its status. The call is intentionally blocking. It fails if port `1455`
is unavailable, the browser cannot be opened, login expires, the caller thread
is interrupted, or OpenAI returns an OAuth/token error.

The returned `AuthStatus` is safe to display selectively. Do not print
`status.metadata()` wholesale because it can contain a refresh token.

## Device Code Login

The generic device-code API starts a flow and returns the verification URL and
user code:

```java
SubscriptionLoginStart start = login.startDeviceCodeSubscriptionLogin(
        new DeviceCodeSubscriptionLoginRequest("openai"));

System.out.println(start.authorizationUri());
System.out.println(start.userCode().orElse(""));

while (true) {
    SubscriptionLoginPollResult result = login.pollSubscriptionLogin(start.flowId());
    if (result.status() == SubscriptionLoginStatus.COMPLETED) {
        break;
    }
    if (result.status() == SubscriptionLoginStatus.FAILED
            || result.status() == SubscriptionLoginStatus.EXPIRED) {
        throw new IllegalStateException(result.error().orElse("device login failed"));
    }
    Thread.sleep(1_000);
}
```

`pollSubscriptionLogin(...)` persists a completed credential. The current
OpenAI/Codex default profile pins the browser flow. Its exact production
device-code protocol is still a Phase 9 gap, so use device code with a custom
OpenAI-compatible subscription profile only until that protocol is implemented.

## Status, Refresh, And Logout

```java
AuthStatus status = login.status("openai");
Optional<AuthSession> refreshed = login.refreshAuth("openai");
boolean removed = login.logout("openai");
```

`status(...)` and `resolveAuth(...)` try to refresh an expired subscription
session when a refresh token is available. `refreshAuth(...)` explicitly
refreshes it. A valid rotated refresh token replaces the old stored token;
otherwise the old refresh token remains. `logout(...)` removes only the named
provider’s stored credential.

## Session Usage

`AgentSession` owns the persisted conversation. Create it once, then call
`prompt(...)` repeatedly without rebuilding history:

```java
AgentSession session = runtime.createSession(new CreateSessionRequest(
        Path.of("/private/path/project-session.jsonl"),
        Path.of("/private/path/project")));

PromptResult first = session.prompt(new PromptRequest("Summarize README.md."));
PromptResult second = session.prompt(new PromptRequest("Turn that into release notes."));
```

To continue later, open the same file with
`runtime.resumeSession(new ResumeSessionRequest(sessionFile))`. Credentials
remain in the user-scoped auth store; session files contain conversation data,
not tokens.

## Runnable Example

[`OpenAiSubscriptionSdkExample`](../agent4j-coding/src/main/java/com/agent4j/coding/sdk/example/OpenAiSubscriptionSdkExample.java)
implements the commands documented above. Set an enabled model first:

```bash
export AGENT4J_OPENAI_MODEL=<enabled-model-id>
```

Run the example from an IDE or through its module-scoped Maven profile:

```bash
mvn -pl agent4j-coding -am test \
  -Dagent4j.runOpenAiExample=true \
  -Dagent4j.openAiExample.args="status"
```

Available commands:

```text
browser
device
status
refresh
logout
prompt <session-file> <prompt text>
```

The example prints only non-secret status fields. Use a private terminal for
interactive login and do not supply tokens through command-line arguments.
