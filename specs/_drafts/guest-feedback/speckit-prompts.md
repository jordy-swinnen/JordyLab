# Guest Feedback and Admin Triage: SpecKit Prompts

Background is in `research.md`, the expected spec is in `spec-draft.md`.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.
> **Order:** works on its own with today's roles (guests see the Game Catalog only). It gets richer once the
> `user-management` draft adds per-app access; specify both before the first release.

---

## `/speckit-specify`

```
Short name: guest-feedback.

Let my guests send me feedback about the apps they use, and let me triage it as tickets and turn the good ones into SpecKit drafts with one click. Essential for the first release.

GUEST SIDE: guests get a Feedback tab in Settings (Settings is admin-only today; open it to guests with only this tab, and keep Users and AI Models admin-only in the backend). They pick one of the apps they are allowed to use (today only the Game Catalog; later whatever per-app access grants them), a type (bug report, feature request, other), a title and a description, and send it. They see their own tickets with status and any reply from me, never other people's tickets. A daily submission limit per guest.

ADMIN SIDE: I get an admin-only Feedback inbox tab (I don't see the guest tab). Each feedback item is a ticket: app, type, sender, date, text. States: New, Implementing, Backlog, Rejected. Backlog keeps it for later and can still become Implementing or Rejected; Reject archives it as rejected (never deleted). I can add a reply the guest sees and a private note they don't. A new ticket sends me an Ntfy notification without the ticket text.

IMPLEMENT: turns the ticket (plus an optional note from me) into a draft for SpecKit in the exact shape of the drafts in specs/_drafts: one folder specs/_drafts/<short-name>/ with research.md, spec-draft.md and speckit-prompts.md, no numbers or codes anywhere. It is written by a new AI feature through ResilientAiService with an Opus-class default model (heavier than my other features), changeable on the AI Models page. I preview the draft, then the app opens a GitHub pull request: new branch from main, commits with the Refs: NO-CODE trailer (the Commit References check needs it), never merged automatically. The ticket links to the pull request. If GitHub fails, the draft is kept and I can retry.

SAFETY: my repository is public, so drafts must never contain the guest's name, email or other personal data. Ticket text is guest input that reaches a model whose output gets committed: pass it as delimited data, never as instructions, keep my preview mandatory, and only accept output that is exactly the draft files in the one draft folder. The GitHub credential is limited to this repository (contents and pull requests), stored as a SOPS secret, never sent to the browser.

Out of scope: feedback from people without an account, voting, merging tickets into one draft, the app implementing anything itself, email notifications.
```

## `/speckit-clarify`: expected questions and suggested answers

- Extra types? → add "improvement"; "question" can be "other".
- Screenshots? → not in the first version (storage and public-repo risk).
- Edit the draft in the app? → regenerate with a note in the first version; full editing later.
- Guest submission limit? → five per day.
- GitHub credential type? → fine-grained personal access token first; a GitHub App if the token's expiry becomes a chore.
