# agent4j-ai

`agent4j-ai` mirrors PI's `@earendil-works/pi-ai` package: provider-neutral
LLM messages, model metadata, auth resolution, provider stream contracts, and
provider adapters.

## PI Provider/Model/Auth Shape

PI keeps endpoint data in both model metadata and resolved auth, but those
fields have different responsibilities.

- `Provider.baseUrl?: string` is provider-level default metadata/config.
- `Model.baseUrl: string` is the effective endpoint used by provider API code.
- `ModelAuth.baseUrl?: string` is an auth/config override, usually resolved
  from settings or environment.

In PI, resolved auth is applied before provider streaming. If auth has a
`baseUrl`, PI creates a request-scoped model copy with that `baseUrl`; then it
calls the provider with the effective model. Provider implementations use
`model.baseUrl` directly.

Conceptually:

```text
catalog/provider model + resolved auth -> effective request model -> provider.stream(...)
```

So `baseUrl` in auth is not an independent endpoint lookup inside each provider.
It is an override that feeds into the request model.

## agent4j Implementation

`AiModel` currently has `Optional<String> baseUrl`, and `AiResolvedAuth` also
has `Optional<String> baseUrl`. That matches PI's broad shape:

- `AiModel.baseUrl()` represents the request-scoped effective endpoint base.
- `AiResolvedAuth.baseUrl()` represents an auth/config override.
- `AiProviderRequest` applies `AiResolvedAuth.baseUrl()` to a request-scoped
  model copy when present, leaving the catalog model unchanged.
- Provider implementations resolve endpoints from `request.model().baseUrl()`
  and their provider default endpoint, not directly from auth context.

For provider APIs with resource-specific paths, adapters append the path they
own when needed:

- OpenAI Responses: `/responses`
- Anthropic Messages: `/messages`

If the configured value already ends with the required path, the adapter reuses
it unchanged. Otherwise it trims trailing slashes and appends the provider path.

This keeps provider stream execution simple: the provider receives the effective
model configuration for that request, while auth resolution remains responsible
for credentials, headers, and endpoint overrides.

## Auth Modes

`AiResolvedAuth.mode()` records how credentials were obtained or should be
interpreted:

- `none`: no resolved credentials
- `api-key`: provider API key, usually from settings or environment
- `access-token`: bearer access token for OpenAI-compatible automation
- `chatgpt-subscription`: bearer access token obtained from a ChatGPT/Codex
  subscription login flow
- `custom-headers`: caller-supplied headers without a standard credential

Phase 8 only makes the provider abstraction ready for these modes. The live
browser/device OAuth login flow, persisted user credential store, auth status,
and logout API remain Phase 9 work.
