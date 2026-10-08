# Contract: LibBot API

Replaces `POST /api/gamecatalog/chat`. Auth: `admin` or `guest`. The guest daily limit (20 messages, `jordylab.settings.guest-chat`) applies; admins are exempt.

## `POST /api/gamecatalog/libbot/ask`

Request (JSON):

```json
{ "conversationId": "7b1f6c2e-…", "message": "There are 6 people here tonight, what should we play?", "attachedGameIds": [] }
```

- `conversationId`: opaque UUID the **client** generates per conversation. The server never uses it alone: the memory key is `jwtSubject + ":" + conversationId`
  (a user cannot address another user's conversation).
- `message`: 1..1000 characters after trimming. `400` otherwise.
- `attachedGameIds`: optional, max 5, each must be visible (the "ask about this game" entry from the game page, as today).

Before the stream opens: `401/403` as usual; **`429 GUEST_LIMIT_REACHED`** JSON (as today) `{ "code": "GUEST_LIMIT_REACHED", "limit": 20, "resetsAt": "…" }`
from the settings-module pre-check. A request that passes returns `200 text/event-stream`.

### Events

Each event is `event: <name>` + `data: <json>`.

| Event | When | Data |
|---|---|---|
| `stage` | immediately, then at each step | `{ "stage": "UNDERSTANDING" \| "SEARCHING" \| "WRITING" }` |
| `answer` | once, success | see below |
| `error` | once, failure | `{ "code": "UNAVAILABLE" \| "INTERNAL", "retryable": true }` |

The stream always ends with exactly one `answer` **or** one `error`. There is no partial answer. Heartbeat comments (`: ping`) every 15 s keep proxies open.

`answer` data:

```json
{
  "outcome": "ANSWERED",
  "language": "en",
  "text": "Two games in your library work for six people on one screen: …",
  "applied": [ "6+ local players", "local multiplayer", "installed" ],
  "unknown": { "count": 31, "note": "For 31 more games the player count isn't known yet, so I can't say whether they fit six." },
  "references": [ { "gameId": "…", "title": "Overcooked! All You Can Eat", "platforms": [ {"name": "PlayStation 5", "background": "#0070D1", "foreground": "#FFFFFF"} ],
                   "cover": { "status": "EXTERNAL_URL", "externalUrl": "…", "localUrl": null } } ],
  "quota": { "limit": 20, "remaining": 14, "resetsAt": "…", "exempt": false }
}
```

| `outcome` | Meaning | `references` |
|---|---|---|
| `ANSWERED` | grounded answer | 1..10, every title appears in `text` |
| `NO_MATCH` | requirements understood, nothing satisfies them and nothing is unknown | `[]` |
| `CLARIFY` | one clarifying question | `[]` |
| `OUT_OF_SCOPE` | polite decline with an example of what LibBot can do | `[]` |
| `EMPTY_LIBRARY` | no visible games | `[]` |

Invariants (each has a test): `applied` and `unknown.note` are produced by server templates in the question's language, never by the model; `unknown` is
present only when unknown-data games could match; `references` is empty for every outcome except `ANSWERED`; at most 10; every reference's title occurs in `text`;
`language ∈ {en, nl}` (for any other language the reply is one fixed English "please rephrase" `OUT_OF_SCOPE`).

### Guest quota

A message counts only after an `answer` event was produced. The gamecatalog module publishes `LibBotMessageAnswered(userSubject, admin)`; the settings module
increments `guest_chat_usage` for non-admins. `error` events, aborted streams (client disconnect before `answer`) and `429`s publish nothing (FR-013).

## `DELETE /api/gamecatalog/libbot/conversations/{conversationId}`

Drops the caller's conversation (the "New conversation" button). `204`, idempotent. Only the caller's own key can match.

## `GET /api/gamecatalog/libbot/quota`

`{ "limit": 20, "remaining": 14, "resetsAt": "…", "exempt": false }`: shown above the composer before the first message.

## Internal contract: interpret and answer models (not HTTP)

Typed records validated with Jakarta Validation (research A2). Documented here because the golden set asserts on them.

```text
QuestionInterpretation {
  intent: LIBRARY_QUERY | GAME_QUESTION | FOLLOW_UP | NEEDS_CLARIFICATION | OUT_OF_SCOPE
  language: en | nl | other
  standaloneQuestion: string            // the question rewritten with the conversation resolved
  facts: {
    partySize: int 1..64 | null         // people present
    playingAlone: boolean | null
    online: boolean | null              // "online with friends"
    platforms: [string]                 // validated against visible platforms
    places: [string]                    // validated against visible host/console labels
    installStatus: INSTALLED | NOT_INSTALLED | null
    genres: [string], releaseYearMin: int | null, releaseYearMax: int | null
    markFilter: WANT_TO_PLAY | PLAYED_LIKED | PLAYED_DISLIKED | null
    markScope: MINE | ALL
    likeMyLiked: boolean                // "something like what I liked"
    semanticQuery: string | null        // mood/taste text to embed
    referencedGameIds: [uuid]           // resolved from the conversation, validated against visible ids
  }
  reply: string | null                  // required for NEEDS_CLARIFICATION and OUT_OF_SCOPE, in `language`
}

LibBotAnswer { text: string, recommendedGameIds: [uuid] }
```

Derivation (Java, unit-tested without a model): `partySize ≥ 2` → `minLocalPlayers = partySize`, `localMultiplayer = true`; `playingAlone` → `singlePlayer = true`;
`online` → `onlineMultiplayer = true`; unknown platform/place names are dropped (never invented); `partySize` above the largest known count yields
`NO_MATCH` with the largest known group size in the text.

## Implementation notes (as built, 2026-10-08)

- **`GET /api/gamecatalog/libbot/quota` is served by the `settings` module** (it owns the allowance and counts answers through the `LibBotMessageAnswered` event); the path is unchanged. The guest pre-check on `POST /libbot/ask` is a servlet filter in `settings` and answers `429 { reason: "CHAT_LIMIT_REACHED", limit, resetsAt }` before the stream opens.
- References carry the same flat cover fields as the catalog summaries (`coverStatus`, `coverUrl`, `coverEndpoint`).
- A question may carry up to 5 attached game ids; one that is not visible answers `400` before the stream opens.
