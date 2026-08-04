# Resource And Settings Parity

Phase 8 pins the current Java resource/settings boundary against PI's coding
agent resource model.

## Directories

Resource discovery uses:

- global agent directory: `~/.pi/agent`
- project agent directory: `<cwd>/.pi`
- global Agent Skills directory: `~/.agents/skills`
- project/ancestor Agent Skills directories: `.agents/skills`

## Loaded Resources

`ResourceLoader` currently discovers:

- context files: global `AGENTS.md`, then ancestor/current `AGENTS.md` with
  same-directory `CLAUDE.md` fallback
- system prompt: project `.pi/SYSTEM.md` overrides global
  `~/.pi/agent/SYSTEM.md`
- append system prompts: global then project `APPEND_SYSTEM.md`
- prompt templates: default prompt directories plus settings `prompts` sources
- skills: PI skills and Agent Skills directories plus settings `skills` sources
- themes: default theme directories, settings `themes`, and CLI-provided theme
  sources
- local package resources: settings `packages`, package `pi.prompts`,
  `pi.skills`, `pi.themes`, and conventional package resource directories

## Settings

Settings files are read from:

- `~/.pi/agent/settings.json`
- `.pi/settings.json`

Global settings are merged before project settings. Nested objects merge
recursively; arrays/scalars replace. Unknown fields are preserved.

Current interpreted keys:

- resource keys: `prompts`, `skills`, `themes`, `packages`
- provider/model keys: `defaultProvider`, `defaultModel`, `models`, `providers`
- auth/provider keys under `providers.<id>`: `apiKey`, `accessToken`,
  `authMode`, `baseUrl`, `expiresAt`, `headers`, `metadata`
- runtime defaults: `retry.maxRetries`, `httpIdleTimeoutMs`

## Trust And Disable Gates

The current Java boundary supports non-interactive project trust gating.
Untrusted discovery skips protected project `.pi` resources and project
`.agents/skills`, while context files still load.

Disable controls live in `ResourceDiscoveryOptions`:

- context files
- prompt templates
- skills
- themes
- packages

## Remaining Later-Phase Work

The following are intentionally not Phase 8 resource-loader work:

- interactive project trust prompting
- `trust.json` persistence
- npm/git package install, update, and reconcile commands
- full coding-session invocation wiring
- exact provider-facing prompt fixture text comparison against PI source
