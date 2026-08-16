# PI OpenAI Built-In Model Catalog

Snapshot date: 2026-08-15.

This reference records the OpenAI model IDs shipped by PI AI version `0.84.2`.
It is a snapshot of PI's built-in provider catalog, not a statement that every
model is enabled for every OpenAI account.

PI provides two distinct OpenAI-backed built-in providers:

| Provider ID | Authentication | Endpoint |
| --- | --- | --- |
| `openai` | `OPENAI_API_KEY` | `https://api.openai.com/v1` |
| `openai-codex` | ChatGPT Plus/Pro OAuth | `https://chatgpt.com/backend-api` |

## `openai`

The API-key provider has the following 38 built-in model IDs:

```text
gpt-4
gpt-4-turbo
gpt-4.1
gpt-4.1-mini
gpt-4.1-nano
gpt-4o
gpt-4o-2024-05-13
gpt-4o-2024-08-06
gpt-4o-2024-11-20
gpt-4o-mini
gpt-5
gpt-5-chat-latest
gpt-5-mini
gpt-5-nano
gpt-5-pro
gpt-5.1
gpt-5.2
gpt-5.2-chat-latest
gpt-5.2-pro
gpt-5.3-chat-latest
gpt-5.3-codex
gpt-5.3-codex-spark
gpt-5.4
gpt-5.4-mini
gpt-5.4-nano
gpt-5.4-pro
gpt-5.5
gpt-5.5-pro
gpt-5.6-luna
gpt-5.6-sol
gpt-5.6-terra
gpt-realtime-2.1
o1
o1-pro
o3
o3-mini
o3-pro
o4-mini
```

## `openai-codex`

The ChatGPT subscription provider has the following seven built-in model IDs:

```text
gpt-5.3-codex-spark
gpt-5.4
gpt-5.4-mini
gpt-5.5
gpt-5.6-luna
gpt-5.6-sol
gpt-5.6-terra
```

## Source and update procedure

PI's provider implementations select `OPENAI_MODELS` and
`OPENAI_CODEX_MODELS` respectively. The corresponding generated JSON catalog
is included in the published `@earendil-works/pi-ai` package under
`dist/providers/data/`.

To refresh this document, inspect the published package version and list the
keys from `openai.json` and `openai-codex.json`; preserve the provider split.

- PI OpenAI provider: <https://github.com/earendil-works/pi/blob/main/packages/ai/src/providers/openai.ts>
- PI OpenAI Codex provider: <https://github.com/earendil-works/pi/blob/main/packages/ai/src/providers/openai-codex.ts>
- Published package: <https://www.npmjs.com/package/@earendil-works/pi-ai/v/0.84.2>
