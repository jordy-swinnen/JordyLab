# Guest Feedback and Admin Triage: Research

Date: 2026-10-05, updated 2026-11-01. Checked against the repo on `main`. Decisions from Jordy are marked as such.

## Idea in one paragraph

Guests get a Feedback tab in Settings where they send bug reports, feature requests and other feedback about the apps
they can use. The admin gets an inbox tab (admin-only), the idea box, where each item is a ticket. For each ticket the
admin decides: **Accept** (the app creates an issue in Linear, Jordy's backlog, labelled with the app and type),
**Backlog** (keep it in the app for later) or **Reject** (archive as rejected). Guests see the status of their own
tickets and an optional reply. Jordy considers this essential for the first release.

## Decisions (Jordy)

- 2026-10-05: Guests see **status plus an optional reply** from the admin.
- 2026-11-01: **Accept creates a Linear issue** in team `JOR` (see the `linear-backlog` draft). This replaces the
  earlier decision that Implement writes a SpecKit draft with an Opus-class model and opens a GitHub pull request from
  the app. Writing the draft becomes a later step, started from the Linear issue by a local agent that can read the
  codebase. The app therefore needs no GitHub credential and no draft-writing AI feature.

- 2026-11-02: the backend uses a **personal API key** (issues show Jordy as creator); the first version has **no status
  sync** from Linear; after Accept a guest sees **Accepted and the optional reply** only.

## Accepting into Linear

- **API:** Linear's GraphQL endpoint `https://api.linear.app/graphql`, mutation `issueCreate` with `teamId`, `title`,
  `description`, `labelIds` and the Triage `stateId` (without a state the issue lands in Triage anyway when Triage is
  on). The backend looks up the team, the Triage state and the App/Type label ids once and caches them.
- **Mapping:** ticket app → App label (Game Catalog → "Game Catalog", …); ticket type → Type label (bug report → Bug,
  feature request → Feature, improvement → Improvement, other → Idea). An unknown app falls back to "Platform".
- **Idempotency:** the ticket stores the issue id once created; a retry first checks for it, so a double click or a
  timeout after Linear accepted the request never makes two issues.
- **Credential (decided: personal API key):** the alternative, an OAuth app installed with `actor=app` makes "JordyLab" the creator of the issue (installing needs
  workspace admin, and the `admin` scope is not allowed with it). A personal API key is simpler but creates issues as
  Jordy. Either one is a SOPS secret like the others, never sent to the browser.
- **Limits:** 2,500 (API key) or 5,000 (OAuth app) requests per hour; the idea box needs a few a day. The Free plan's
  issue cap (about 250) is the real limit, so Backlog and Reject stay in the app and only accepted ideas reach Linear.
- **Status back to the guest (later version, not in the first):** a Linear webhook on issue updates maps Linear states to guest-visible ones
  (Backlog/Drafted/Specified → Planned, In Progress/In Review → In progress, Done → Done, Canceled → Not planned).
  Deliveries are signed (`Linear-Signature`, HMAC-SHA256 of the raw body) and must be rejected when `webhookTimestamp`
  is more than 60 seconds old; Linear waits 5 seconds for an answer.
- **No new module boundary trouble:** the Linear client is small (one mutation, a few lookups); it lives in the
  `feedback` module unless another module needs Linear too, then it moves to `shared`.

## What the repo has today

- **Settings is admin-only.** The `settings` route uses `roleGuard('admin')`; its tabs are Users and AI Models. A guest
  Feedback tab means opening Settings to guests with only that tab visible, and keeping Users and AI Models admin-only
  in the backend (deny by default), not just hidden.
- **Roles:** realm roles `admin` and `guest` (plus service roles). Guests are hard-wired to the Game Catalog today.
  "Apps the guest can use" therefore means only the Game Catalog until per-app access exists (see the
  `user-management` draft). The feedback form should read the list from the same access source, so it widens
  automatically once grants exist.
- **AI routing:** no AI call is needed any more. (Earlier versions of this draft added a draft-writing AI feature; that
  moved to the local agent working from the Linear issue.)
- **Notifications:** Ntfy is already used for "new sign-up pending" (`PendingSignupWatcherService`, `NtfyClient`). A
  new ticket can notify the admin the same way.
- **Linear:** the workspace `linear.app/jordylab`, team `JOR`, Free plan, with App and Type label groups and a Triage
  state, set up by the `linear-backlog` draft.

## Risks to design for

- **Personal data.** Linear is private, but the issue still carries no guest name or email: it says "a guest" and the
  app. Issue text may later be quoted in drafts in the public repository.
- **Prompt injection.** Ticket text is written by guests and is later read by agents working from the Linear issue.
  The issue marks it as guest text, and the agent skills treat issue text as data, never as instructions.
- **Abuse.** Guests could flood the inbox: a per-guest daily submission limit. Only accepted tickets reach Linear, so
  spam never uses up the Free plan's issue cap.
- **Two places for one idea.** After Accept, Linear is the source of truth; the ticket keeps only its guest-visible
  status and reply.

## Open questions

- Feedback types beyond bug report and feature request: "improvement" and "other"? A "question" type?
- Screenshots on bug reports: allowed? (Storage, size limits, and uploading them to Linear.)
- Can a backlog ticket be accepted or rejected later? (Expected: yes.)
