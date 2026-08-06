# OpenAI Subscription Login Flow

This document explains the browser OAuth/PKCE flow we are adding for
ChatGPT/Codex subscription login, and how the current Java implementation maps
that flow into SDK components.

## OAuth And PKCE

Browser login uses OAuth authorization-code flow with PKCE.

The important values are:

- `code`: a short-lived authorization code returned to our callback URL after
  the user signs in. It is not the access token.
- `state`: a random value generated before opening the browser. The auth server
  returns the same value in the callback. We use it to reject callbacks that do
  not belong to the login we started.
- `code_verifier`: a random secret generated before opening the browser. It
  stays local and is not sent in the browser authorization URL.
- `code_challenge`: the public PKCE challenge derived from `code_verifier`.
- `code_challenge_method=S256`: tells the auth server that the challenge is
  `BASE64URL(SHA256(code_verifier))`.

The PKCE check works like this:

1. Before browser login, the client creates a secret `code_verifier`.
2. The client sends only `code_challenge` and `code_challenge_method=S256` in
   the browser URL.
3. After the callback returns `code`, the client sends `code_verifier` directly
   to the token endpoint.
4. The token endpoint checks:

   ```text
   BASE64URL(SHA256(code_verifier)) == original code_challenge
   ```

If someone steals only the callback `code`, they still cannot exchange it for
an access token without the original `code_verifier`.

The callback normally contains only:

```text
http://localhost:1455/auth/callback?code=<temporary-code>&state=<same-state>
```

The access token is intentionally not returned through the browser redirect.
The client exchanges the temporary code with a direct token request:

```text
grant_type=authorization_code
code=<temporary-code>
client_id=<client-id>
redirect_uri=<same-callback-uri>
code_verifier=<local-secret>
```

The token endpoint then returns fields such as `access_token`, `expires_in`,
`refresh_token`, `token_type`, `scope`, and plan/account metadata when
available.

When a refresh token is available, the client can later refresh an expired
session without opening a browser:

```text
grant_type=refresh_token
refresh_token=<stored-refresh-token>
client_id=<client-id>
```

The token endpoint returns a new `access_token` and expiry. If it also returns a
new `refresh_token`, the stored credential rotates to the new value. If it does
not return one, the previous refresh token is retained in metadata.

## OAuth URI Roles

The browser login implementation uses several different URI values. They are
not interchangeable.

`authorizationEndpoint` is the OAuth browser-login endpoint. It is used in
`OpenAiSubscriptionLoginClient.startBrowserLogin(...)` to build
`SubscriptionLoginStart.authorizationUri()`.

Example shape:

```text
https://auth.example.test/authorize?...query...
```

The browser opens this URL. The query contains values such as `client_id`,
`redirect_uri`, `state`, `code_challenge`, `code_challenge_method`, and scopes.
Its purpose is to start user login in the browser.

`redirectUri` is our callback URL. It is usually local:

```text
http://localhost:1455/auth/callback
```

In `startBrowserLogin(...)`, we include it in the authorization URL so the auth
server knows where to send the browser after sign-in:

```text
redirect_uri=http://localhost:1455/auth/callback
```

After the user signs in, the browser returns to:

```text
http://localhost:1455/auth/callback?code=<temporary-code>&state=<same-state>
```

In `completeBrowserLogin(...)`, the client sends the same `redirect_uri` again
to the token endpoint. The auth server checks that it matches the original
authorization request. Its purpose is both browser return routing and
token-exchange validation.

`tokenEndpoint` is the direct token-exchange endpoint. It is not opened in the
browser. It is used in `completeBrowserLogin(...)` after the callback returns a
temporary `code`.

Example shape:

```text
https://auth.example.test/token
```

The client posts:

```text
grant_type=authorization_code
code=<temporary-code>
client_id=<client-id>
redirect_uri=<same-callback-uri>
code_verifier=<local-secret>
```

If the request is valid, the token endpoint returns credential fields such as
`access_token`, `expires_in`, and `refresh_token`.

`baseUrl` is not an OAuth endpoint. It is the later provider/service base URL
attached to the resolved auth, for example:

```text
https://codex.openai.com/api
```

`startBrowserLogin(...)` stores `request.baseUrl().or(() -> options.baseUrl())`
inside the local browser flow. After token exchange, `completion(...)` copies it
into `SubscriptionLoginCompletion.baseUrl()`. `DefaultLoginService` then saves
it inside `AiResolvedAuth.chatGptSubscription(...)`.

Later, when `AgentSession.prompt(...)` runs, provider auth resolution can use
that base URL for authenticated provider/Codex API calls.

Short version:

```text
authorizationEndpoint = browser login page
redirectUri           = our callback URL
tokenEndpoint         = direct backend token exchange URL
baseUrl               = later API/provider base URL after auth succeeds
```

## Current Java Component Flow

The SDK-facing entrypoint is `LoginService`.

Typical construction:

```java
LoginService loginService = new DefaultLoginService(
        PersistentAuthCredentialStore.userDefault(),
        Clock.systemUTC(),
        new OpenAiSubscriptionLoginClient(options));
```

For SDK users, `CodingAgentRuntimeServices.withOpenAi(...)` wires the standard
OpenAI Responses provider, provider registry, persistent auth store, and the
standard Codex subscription login client:

```java
AiModelReference model = new AiModelReference("openai", "gpt-5");

CodingAgentRuntimeServices services =
        CodingAgentRuntimeServices.withOpenAi(
                OpenAiCodingRuntimeOptions.builder(model)
                        .build());

AgentSessionRuntime runtime = new CodingAgentSessionRuntime(services);
```

`OpenAiCodingRuntimeOptions` uses `OpenAiSubscriptionLoginClientOptions.codexDefaults()`
unless a caller supplies a custom `subscriptionLogin(...)` profile. OAuth
endpoints remain explicit in `OpenAiSubscriptionLoginClientOptions`.

Component responsibilities:

- `DefaultLoginService`: exposes SDK login APIs and persists completed auth.
- `PersistentAuthCredentialStore`: stores final `AuthSession` records outside
  the project at `~/.pi/agent/auth.json` by default.
- `OpenAiCodingRuntimeOptions`: SDK convenience options for the standard OpenAI
  runtime wiring.
- `OpenAiSubscriptionLoginClient`: owns OAuth mechanics for browser and
  device-code login.
- `OpenAiSubscriptionLoginClientOptions`: supplies client ID, authorization
  endpoint, token endpoint, optional device endpoint, redirect URI, scopes,
  headers, and provider base URL.
- `OpenAiSubscriptionLoginHttpTransport`: sends form-encoded OAuth requests.

The normal SDK API is one call:

```java
AuthStatus status = loginService.loginOpenAiSubscription();
```

`DefaultLoginService.loginOpenAiSubscription()` binds the registered localhost
callback URI, starts `OpenAiSubscriptionLoginClient`, opens the system browser,
waits for the callback, persists the completed credential, closes the callback
server, and returns the resulting `AuthStatus`.

The one-call path is single-use and lifecycle-bounded: it waits only until the
OAuth flow expiry, removes its temporary flow state on success, timeout,
browser-launch failure, or thread interruption, and closes the local server in
every case. An OAuth `error` callback removes the matching flow by `state`.
The callback server returns `409` for duplicate callbacks and completes a
pending wait with a failed result when it is shut down.

Token responses are validated before credentials are persisted. `access_token`
must be nonblank; a present `refresh_token` must be nonblank; `token_type`, when
present, must be `Bearer`; and `expires_in`/`expires_at`, when present, must be
valid future expiry values. Refresh responses retain the existing refresh token
when no replacement is supplied and replace it only with a valid rotated token.
Malformed browser or device token payloads are rejected and remove their
temporary flow state.

Internally, `OpenAiSubscriptionLoginClient.startBrowserLogin(...)` generates:

- local `flowId`
- OAuth `state`
- PKCE `code_verifier`
- PKCE `code_challenge`
- expiry timestamp

It stores a local `BrowserFlow` keyed by `flowId`, then returns a
`SubscriptionLoginStart` containing `authorizationUri`.

After browser sign-in, the callback returns `code` and `state`. The callback
completion API is:

```java
SubscriptionLoginPollResult result =
        loginService.completeBrowserSubscriptionLoginCallback(
                code,
                state);
```

That path:

1. Maps callback `state` back to the local `flowId`.
2. Verifies the flow has not expired.
3. Verifies the callback `state` matches the stored `state`.
4. Posts the authorization-code form to the token endpoint.
5. Builds `SubscriptionLoginCompletion` from the token response.
6. Persists the credential through `DefaultLoginService`.

For browser login callback hosting, `BrowserSubscriptionLoginCallbackServer`
starts a local HTTP server, exposes `redirectUri()`, parses callback query
parameters, calls `LoginService.completeBrowserSubscriptionLoginCallback(...)`,
and exposes a completion future:

```java
try (BrowserSubscriptionLoginCallbackServer callbackServer =
             BrowserSubscriptionLoginCallbackServer.start(loginService)) {
    SubscriptionLoginStart start =
            loginService.startBrowserSubscriptionLogin(
                    new BrowserSubscriptionLoginRequest(
                            "openai",
                            Optional.empty(),
                            Map.of(),
                            Optional.of(callbackServer.redirectUri())));

    // Open start.authorizationUri() in a browser.
    SubscriptionLoginPollResult result = callbackServer.completion().join();
}
```

For device-code login, `DefaultLoginService.pollSubscriptionLogin(flowId)`
already persists completed credentials automatically:

```java
SubscriptionLoginStart start =
        loginService.startDeviceCodeSubscriptionLogin(
                new DeviceCodeSubscriptionLoginRequest("openai"));

SubscriptionLoginPollResult result =
        loginService.pollSubscriptionLogin(start.flowId());
```

When polling returns `COMPLETED`, `DefaultLoginService` stores the resulting
`AiResolvedAuth.chatGptSubscription(...)`.

For token refresh, `DefaultLoginService` exposes:

```java
Optional<AuthSession> refreshed = loginService.refreshAuth("openai");
```

It also attempts refresh automatically when `status("openai")` or
`resolveAuth("openai")` sees an expired ChatGPT subscription session with a
stored refresh token. `AuthStatus.metadata()` exposes provider metadata such as
`plan`, `accountId`, and `refreshToken` when available.

Later, `AgentSession.prompt(...)` uses stored auth through:

1. `CodingAgentSessionRuntime` resolves the provider/model.
2. It calls `LoginService.resolveAuth("openai")`.
3. It creates a provider-backed `AgentLoop` with that `AiResolvedAuth`.
4. `OpenAiResponsesProvider` sends the access token as:

   ```text
   Authorization: Bearer <access-token>
   ```

## Current Design Gaps

The current implementation deliberately keeps OAuth endpoints configurable
because the public Codex docs describe the login flow but do not publish enough
endpoint-level token contract to hard-code OpenAI defaults safely.

Known gaps before the browser-login UX is complete:

- Opening the system browser is not implemented yet.
- Exact OpenAI endpoint defaults are not pinned yet.
- Live integration tests are not implemented yet.
