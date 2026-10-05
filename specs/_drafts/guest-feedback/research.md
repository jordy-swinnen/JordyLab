# Guest Feedback and Admin Triage: Research

Date: 2026-10-05. Checked against the repo on `main`. Decisions from Jordy are marked as such.

## Idea in one paragraph

Guests get a Feedback tab in Settings where they send bug reports, feature requests and other feedback about the apps
they can use. The admin gets an inbox tab (admin-only) where each item is a ticket. For each ticket the admin decides:
**Implement** (an AI writes a draft for SpecKit, in the same shape as the drafts in `specs/_drafts`, and the app opens a
GitHub pull request with it), **Backlog** (keep for later) or **Reject** (archive as rejected). Guests see the status of
their own tickets and an optional reply. Jordy considers this essential for the first release.

## Decisions (Jordy, 2026-10-05)

- Implement opens a **GitHub pull request** from the app, containing the draft folder.
- The draft is written by its own AI feature with an **Opus-class default model** (heavier than the other features),
  changeable on the AI Models page like every other feature.
- Guests see **status plus an optional reply** from the admin.

## What the repo has today

- **Settings is admin-only.** The `settings` route uses `roleGuard('admin')`; its tabs are Users and AI Models. A guest
  Feedback tab means opening Settings to guests with only that tab visible, and keeping Users and AI Models admin-only
  in the backend (deny by default), not just hidden.
- **Roles:** realm roles `admin` and `guest` (plus service roles). Guests are hard-wired to the Game Catalog today.
  "Apps the guest can use" therefore means only the Game Catalog until per-app access exists (see the
  `user-management` draft). The feedback form should read the list from the same access source, so it widens
  automatically once grants exist.
- **AI routing:** every call goes through `ResilientAiService` with an `AiFeature`; defaults live in
  `jordylab.ai.features` (Sonnet 5 for the FNA briefing, Haiku 4.5 for the catalog features), OpenRouter first and one
  retry on Anthropic. A new feature key for writing drafts fits this unchanged. (Jordy mentioned "jevrouter"; nothing
  by that name exists in the repo. The features run on Claude models through OpenRouter.)
- **Notifications:** Ntfy is already used for "new sign-up pending" (`PendingSignupWatcherService`, `NtfyClient`). A
  new ticket can notify the admin the same way.
- **Drafts convention:** `specs/_drafts/<short-name>/` with `research.md`, `spec-draft.md`, optional `plan-draft.md`
  and `speckit-prompts.md`, and no numbers or codes anywhere (rule in the root `AGENTS.md`). The AI must follow it.
- **Commit rules:** every commit needs a `Refs:` trailer, checked on every pull-request commit by the Commit
  References workflow. Commits the app makes must carry `Refs: NO-CODE`, or the pull request fails that check.

## Opening a pull request from the app

- The backend needs a GitHub credential limited to this one repository with write access to contents and pull
  requests only: a fine-grained personal access token, or a GitHub App installation token. Stored as a SOPS secret like
  the others, never sent to the browser.
- Flow: create a branch from `main`, add the draft files, commit (with the trailer), open a pull request. The pull
  request is never merged automatically.
- Failure (GitHub down, token expired) must leave the ticket in a retryable state, not lose the draft.

## Risks to design for

- **The repository is public.** Anything in a draft becomes public. Drafts must not contain the guest's name, email or
  other personal data. The admin previews the draft before the pull request is opened.
- **Prompt injection.** Ticket text is written by guests and goes into a model whose output is committed to the repo.
  Treat ticket text strictly as data (delimited, never as instructions), keep the admin preview step mandatory, and
  limit what the output can contain (only the draft files, only under `specs/_drafts/<short-name>/`).
- **Cost.** An Opus-class model per Implement click is the most expensive call in the app. It only runs on the admin's
  explicit click, and it is counted like every other AI call.
- **Abuse.** Guests could flood the inbox: a per-guest daily submission limit.
- **No repository context.** The model in production cannot read the codebase, so its draft is a starting point: the
  research file says what still needs checking against the repo, and the local agent completes it.

## Open questions

- Feedback types beyond bug report and feature request: "improvement" and "other"? A "question" type?
- Screenshots on bug reports: allowed? (Storage, size limits, and they must never go into the public repo.)
- Can the admin edit the AI's draft in the app before opening the pull request, or only regenerate it?
- Can a backlog ticket be moved to Implement or Reject later? (Expected: yes.)
- Should several tickets be combinable into one draft?
