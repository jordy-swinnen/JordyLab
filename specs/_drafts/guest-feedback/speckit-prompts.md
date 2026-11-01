# Guest Feedback and Admin Triage: SpecKit Prompts

Background is in `research.md`, the expected spec is in `spec-draft.md`.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.
> **Order:** works on its own with today's roles (guests see the Game Catalog only). It gets richer once the
> `user-management` draft adds per-app access; specify both before the first release. Accept needs the Linear
> workspace from the `linear-backlog` draft (team, Triage state, App and Type labels).

---

## `/speckit-specify`

```
Short name: guest-feedback.

Let my guests send me feedback about the apps they use, and let me triage it as tickets in an idea box and accept the good ones straight into my Linear backlog. Essential for the first release.

GUEST SIDE: guests get a Feedback tab in Settings (Settings is admin-only today; open it to guests with only this tab, and keep Users and AI Models admin-only in the backend). They pick one of the apps they are allowed to use (today only the Game Catalog; later whatever per-app access grants them), a type (bug report, feature request, other), a title and a description, and send it. They see their own tickets with status and any reply from me, never other people's tickets. A daily submission limit per guest.

ADMIN SIDE: I get an admin-only Feedback inbox tab (I don't see the guest tab). Each feedback item is a ticket: app, type, sender, date, text. States: New, Accepted, Backlog, Rejected. Backlog keeps it in the app for later and can still become Accepted or Rejected; Reject archives it as rejected (never deleted). I can add a reply the guest sees and a private note they don't. A new ticket sends me an Ntfy notification without the ticket text.

ACCEPT: creates one issue in my Linear workspace (team JOR, Triage state) through Linear's GraphQL API (issueCreate), with the ticket's title and description, my optional note, the App label mapped from the ticket's app and the Type label mapped from its type (bug report → Bug, feature request → Feature, other → Idea). Idempotent: a retry or double click never makes a second issue. The ticket stores the issue id and link (only I see the link). If Linear fails, the ticket stays New with the error and I can retry. Writing a SpecKit draft is not part of Accept; my local agent does that later from the Linear issue.

SAFETY: the issue never contains the guest's name, email or other personal data (it says "a guest" and the app), and marks the text as guest-written so agents treat it as data. The Linear credential (an OAuth app installed with actor=app so issues show JordyLab as creator, or a personal API key) is a SOPS secret, never sent to the browser.

OPTIONAL (Low): a signed Linear webhook (verify Linear-Signature, reject deliveries older than 60 seconds) moves the guest-visible status: Planned, In progress, Done, Not planned.

Out of scope: feedback from people without an account, voting, merging tickets into one issue, writing drafts or opening pull requests from the app, the app implementing anything itself, email notifications.
```

## `/speckit-clarify`: expected questions and suggested answers

- Extra types? → add "improvement"; "question" can be "other".
- Screenshots? → not in the first version (storage, and uploading them to Linear).
- Guest submission limit? → five per day.
- Linear credential? → OAuth app with actor=app, so issues show JordyLab as creator; personal API key only as a stopgap.
- Status sync from Linear? → later; the first version shows Accepted until the admin changes it.
