# Guest Feedback and Admin Triage: Data Model (Phase 1)

One aggregate, one table, four enums. Column names below are the contract; the Flyway migration (`V20261103002__feedback_create_tables.sql`, schema `feedback`) implements them.

## FeedbackTicket (aggregate root)

`feedback.feedback_ticket`, extends `BaseEntity<FeedbackTicket>` (UUID id, `created_at`, `updated_at`, equals/hashCode inherited — never overridden, verified by `EqualsVerifier`).

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Assigned in `build()` when absent. |
| `app` | `FeedbackApp` (enum, string) | Not null. Validated against the sender's allowed apps at submission (FR-005). |
| `type` | `FeedbackType` (enum, string) | Not null. One of the four types. |
| `title` | String ≤ 120 | Not blank after trim. Guarded in `build()`. |
| `description` | String ≤ 5 000 | Not blank after trim. Guarded in `build()`. |
| `sender_subject` | UUID-as-string (36) | Not null. The JWT `sub` — the only guest identifier that is *not* personal data by itself. |
| `sender_display_name` | String, nullable | Snapshot of the token's display name at submission; shown in the inbox. Cleared (anonymised) when the sender's access is removed. |
| `state` | `FeedbackTicketState` (enum, string) | Not null; starts `NEW`. Transitions below — enforced by named mutation methods, never raw setters. |
| `reply` | String ≤ 5 000, nullable | The one guest-visible admin text (FR-008). |
| `private_note` | String ≤ 5 000, nullable | The one admin-only text; included in the Linear issue when the ticket is accepted with one set. |
| `linear_issue_id` | String ≤ 64, nullable | Set exactly once, together with `linear_issue_url`, by `markAccepted`. Idempotency anchor: non-null ⇒ never call Linear again for this ticket. |
| `linear_issue_url` | String ≤ 512, nullable | Admin-only (FR-013). |
| `accept_error` | String ≤ 1 000, nullable | Last Linear failure message; visible to the admin only; cleared on the next successful accept or successful transition. |

Indexes: `(sender_subject, created_at)` — serves the guest's own-ticket list and the daily-limit count. No uniqueness beyond the PK: duplicate titles by the same guest are legitimate (the daily limit is the abuse guard).

Builder guards (`Preconditions.checkArgument` in the partial `FeedbackTicketBuilder.build()` only): `app`, `type`, `title`, `description`, `sender_subject`, `state` non-null; title/description non-blank and within the length caps.

### Named mutation methods (domain logic lives here)

- `moveToBacklog()` — allowed from `NEW` only. `BACKLOG` is "keep for later".
- `reject()` — allowed from `NEW` and `BACKLOG`. Terminal (FR-007).
- `markAccepted(linearIssueId, linearIssueUrl)` — allowed from `NEW` and `BACKLOG`; sets both Linear fields and clears `accept_error`. Terminal.
- `updateTexts(reply, privateNote)` — allowed in any state (the admin can reply to a rejected ticket too); both texts are replaced wholesale (empty string clears); blank-check + length caps.
- `recordAcceptError(message)` — allowed from `NEW`/`BACKLOG`; stores the truncated failure; the state does **not** move (FR-019).
- `anonymiseSender()` — clears `sender_display_name`; any state; fires no event.

Every illegal transition throws (fail fast) → mapped to `409 INVALID_STATE` by the exception handler.

### Domain event

`FeedbackTicketCreated(senderName, appName, typeName)` — registered in the creation path, published on commit, consumed by `mobile`'s notification listener. Payload deliberately carries no ticket text: the notification body is built from these three fields only (FR-017). It lives at the `feedback` module root (public API).

## State machine

```text
NEW ──accept──▶ ACCEPTED (terminal; linear_issue_id + url set)
NEW ──backlog─▶ BACKLOG ──accept──▶ ACCEPTED
NEW ──reject──▶ REJECTED (terminal, archived — view-only)
BACKLOG ──reject──▶ REJECTED
ACCEPTED, REJECTED: no outgoing transitions
```

- "Accept" as a user action = the `AcceptTicketService` flow (Linear call, then `markAccepted`); the state only becomes `ACCEPTED` after the issue id is in hand — a failed accept leaves the ticket in its prior state with `accept_error` set (FR-019), which is also the retry signal.
- Rejected is final per the clarify decision; the archive stays browsable by the admin, never deleted.

## Enums (module-owned, wire contract)

| Enum | Values (wire name) | Display | Linear Type/App label |
|---|---|---|---|
| `FeedbackApp` | `GAME_CATALOG` | Game Catalog | App label "Game Catalog" (fallback label "Platform" when Linear has no match — FR-010) |
| `FeedbackType` | `BUG_REPORT`, `FEATURE_REQUEST`, `IMPROVEMENT`, `IDEA` | Bug report, Feature request, Improvement, Idea | Bug, Feature, Improvement, Idea |
| `FeedbackTicketState` | `NEW`, `ACCEPTED`, `BACKLOG`, `REJECTED` | (shown as-is to both roles) | — (Linear state is always Triage at creation; no mapping back in v1) |

A guest's selectable apps come from `FeedbackAppAccessService` (roles today: `ROLE_guest` → `GAME_CATALOG`); the submit validation uses the same service, so form and validation cannot drift.

## Repository queries (semantics)

- `findBySenderSubjectOrderByCreatedDateDesc(subject)` — the guest's own list, newest first. Guests have no by-id endpoint; another guest's id is unreachable (404 on unknown id in inbox context, 403 by role for guests).
- `countBySenderSubjectAndCreatedDateAfter(subject, startOfDay)` — the daily limit check (five).
- `findAllByStateOrderByCreatedDateDesc(state?)` — the inbox; optional state filter, newest first; no pagination at this volume.
- `findByIdForAccept(id)` — pessimistic-write-lock finder used by the accept flow (serialises double clicks; held only for the duration of the short-timeout Linear call).
- `findBySenderSubject(senderSubject)` — the `UserAccessRemoved` listener's anonymisation pass.

## Relationships to the rest of the platform

- **No foreign keys** to other modules' tables: the sender is referenced by subject string only (Keycloak is the user store; `settings` owns user administration), and the Linear issue by id/url strings. Schema-per-module isolation stays intact.
- **Events in**: `shared.event.UserAccessRemoved` → anonymise that sender's tickets (personal data out, tickets stay — spec edge case).
- **Events out**: `FeedbackTicketCreated` → `mobile` (Ntfy notification).
- **External**: the accepted ticket's `linear_issue_id`/`url` point at Linear team `JOR` (Triage); after acceptance Linear is the source of truth for the idea, the ticket keeps only the guest-visible state and texts.
