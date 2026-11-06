# Status: what is built, what is replaced, where it ends

Last reviewed: 2026-11-05. Statuses are plain words derived from each item's work tracker (Done counts as
**Built**, in progress as **In progress**, everything not started as **Planned**). Open-task counts come from each
feature's task list in `specs/`. The rule that keeps this page current is in
[AGENTS.md](../AGENTS.md#workflow), under "Keeping the drawing and status current".

## Built

| Feature | Status | Open tasks |
|---|---|---|
| FNA finance news, MVP | Built | 0 |
| Game catalog | Built | 0 |
| Game catalog Python scanner | Built | 0 |
| Game catalog refinements | Built | 0 |
| Steam library and family library | Built | 0 |
| Settings: users and AI models | Built | 0 |
| Nintendo Switch games (manual add) | Built | 0 |
| Production end-to-end test, bug log and fix loop | Built | 0 |
| Technical improvements: lint, AI conventions, E2E tests | Built | 0 |
| Game catalog AI refinement (LibBot) | Built, to confirm | 3 |
| Console player counts from a second source | Built | 0 |
| User management control panel | Built, to confirm | 3 |
| Linear as backlog: labels, saved views, sync skills | Built | 0 |
| App redesign with six themes | Built | 0 |
| FNA stock wishlist | Built | 0 |
| Guest feedback and admin triage | Built | 0 |
| Built-in sign-in with passwords and passkeys | Built | 1 |
| Continuous deploy on green main | Built | 0 |
| Production on Docker Compose | Built | 0 |
| Progressive web app (installable, replaces the Android app) | Built | 0 |

## In progress

| Feature | Status | Open tasks |
|---|---|---|
| Read-only MCP connector for Claude (admin only) | In progress | 15 (mostly production checks) |
| Post-Keycloak housekeeping: secrets, database role, CI secrets | In progress | see the Replaced table |
| Permanent agent test account, local and production | In progress | no task list |

## Planned

| Feature | Status | Notes |
|---|---|---|
| Eufy presence: arm and disarm from the app | Planned | 0 of 68 tasks. The spec still assumes the Android app, so it needs rework for the PWA before work starts. |
| Custom Android widget for Eufy (replaces the Home Assistant prototype) | Planned | Spike only. |
| Game library sync app: in-app updates and auto-detection | Planned | No spec yet. |
| Player counts for games no source covers | Planned | No spec yet. |
| Recipe management | Planned | Module not built. No spec yet. |
| Trading signals with human approval | Planned | Module not built. No spec yet. |
| Garmin health data | Planned | Sidecar code is in the repository, but it is not in the production stack. No spec yet. |

Drafts that have no spec yet are in [`specs/_drafts/`](../specs/_drafts/).

## Replaced

| Was | Now | State |
|---|---|---|
| Kubernetes on k3s, with Traefik, cert-manager and CloudNativePG | Docker Compose behind Caddy, with pgBackRest | Built. k3s is retired. |
| Keycloak for sign-in | Built-in sign-in with passwords and passkeys | Built. Cleanup of leftover secrets, database role and CI secrets in progress. |
| Capacitor Android app (APK) | Progressive web app | Built |
| Local model inference (Ollama) | OpenRouter, with Anthropic as fallback | Removed on 2026-09-30 |
| Home Assistant prototype for Eufy | Custom Android widget | Planned |
| Linear as the ticket tracker | Self-hosted Kaneo | Planned. Not started; Linear is still the tracker. |
| AI prompts kept in code | Prompts managed in Langfuse | Planned |

## End goal

The end state is reached when all of these hold:

1. Every built feature has all its tasks checked. The open items above are either finished or dropped.
2. Nothing in the Replaced table still runs in production: the Keycloak cleanup is finished, Kaneo replaces Linear,
   and the prompts are managed in Langfuse.
3. Every planned module either has a spec and is built, or is dropped with a note on this page.
4. The architecture drawing in the [README](../README.md) and the workflow in [ai-workflow.md](ai-workflow.md) match
   the repository. This is checked on every change that touches them.

## Known gaps

- Many spec headers still say Draft. The task lists are the source of truth, not the headers.
- Spec numbers 014, 016 and 019 are each used by two features, so features are named here by what they are.
- Three features are marked Built, but their task lists still have open items (see the Built table). Each needs
  checking: either the tasks are done, or they are dropped.
- Garmin health data is in the repository but not in the production stack, and nothing is planned for it yet.
