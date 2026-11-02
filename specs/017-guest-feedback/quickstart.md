# Guest Feedback and Admin Triage: Quickstart Validation (Phase 1)

How to prove the feature works end to end. Field shapes are in [contracts/rest-api.md](contracts/rest-api.md), the Linear side in [contracts/linear-client.md](contracts/linear-client.md), the state machine in [data-model.md](data-model.md) — this file only sequences them.

## Prerequisites

- Podman running; dev stack up: `podman compose up -d` (Postgres + Keycloak, from `jordylab-be/compose.yaml`)
- A **guest** account in the dev realm (sign one up and approve it through Settings → Users, or use an existing guest) and the **admin** dev user
- Optional for the real accept path: `LINEAR_API_KEY` exported in the backend's environment (Jordy's personal key — never print it). Without it, accept fails gracefully with `LINEAR_UNAVAILABLE`, which is itself scenario 5 below.
- The Linear workspace (team `JOR`, Triage state, App/Type labels) — set up by spec 015; nothing to do here.

## Automated validation (no secrets needed)

```bash
# Backend (Podman host: export the Testcontainers socket first — see jordylab-be/AGENTS.md)
cd jordylab-be
./gradlew :test --tests "*FeedbackModuleTest*" --tests "*LinearClientTest*" \
  --tests "*FeedbackTicketTest*" --tests "*RoleMatrixTest*" --tests "*ModularityTests*"

# Frontend
cd jordylab-fe
bunx nx test feedback-api && bunx nx test feedback-ui
bunx nx run-many -t lint          # ESLint + Oxlint
tools/lint-changed.sh <file>      # after any single-file edit while iterating

# Whole-journey regression (throwaway stack; no LINEAR_API_KEY present by design)
e2e/run.sh web
```

Expected: all green. `LinearClientTest` proves the GraphQL request bodies/headers against WireMock; `FeedbackModuleTest` covers submission, the daily limit, state transitions, own-list isolation and anonymisation; `RoleMatrixTest` pins the access matrix; the Playwright journey (`apps/jordylab-e2e/src/feedback.spec.ts`) drives guest submit → admin triage → accept and asserts the graceful Linear-unavailable state.

## Manual end-to-end (the real accept path — needs `LINEAR_API_KEY`)

Start the backend and the app: `./gradlew bootRun` (with the key) and `bunx nx serve jordylab`.

| # | Scenario | Expected outcome |
|---|---|---|
| 1 | As the **guest**: Settings → Feedback → pick app (only Game Catalog offered), type, title, description → send | Ticket appears in the guest's list as **New**, within a minute start-to-finish (SC-001). No other tabs visible. |
| 2 | The **admin**'s phone (Ntfy) | One notification: "{sender} sent a {type} for the {app} app." — no ticket text (SC-005). |
| 3 | As the **admin**: Settings → Feedback inbox | The ticket lists app, type, sender, date, title, description; state New. |
| 4 | Accept with a private note | Ticket → **Accepted** with the Linear link (admin only). In Linear team `JOR`, Triage: one issue, App + Type labels, description = guest text + "written by a guest" marking + note + `Source: idea box`; **no** guest name/email anywhere (SC-002, SC-003). |
| 5 | As the **guest** again | Own ticket shows **Accepted** and the reply (if the admin set one) — never the Linear link, never the note. |
| 6 | Restart the backend **without** the key; accept another ticket | Accept fails with a visible error; ticket stays **New**; with the key back, retry succeeds — still exactly one issue (idempotency, FR-019). |
| 7 | Click Accept twice quickly on one ticket | One Linear issue only (SC-002). |
| 8 | As the **guest**: submit five tickets, then a sixth | Sixth refused with the limit message (FR-016). |
| 9 | Backlog one ticket, reject another | Backlog ticket stays actionable (can still accept/reject); rejected one shows as Rejected, archived, no actions (FR-007). |
| 10 | Sign in as a second guest | Only their own tickets, ever (SC-004). |

Scenario 4 and 6–7 touch the real Linear workspace — that is intended (a few issues, deletable afterwards). Everything paid/quota-consuming stays out of the automated suites.

## Not covered by this guide

Real Ntfy delivery to the phone (needs the prod topic or a local listener — the client itself is WireMock-tested), the mobile WebView specifics (same build, covered by the existing mobile journeys' scope), and per-app access widening (does not exist yet — `FeedbackAppAccessService` is the seam).
