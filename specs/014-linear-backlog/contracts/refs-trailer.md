# Contract: `Refs:` commit trailer

Checked by `.githooks/commit-msg` locally and by the Commit References workflow on every pull-request commit (same
script). Format-only for Linear keys: it never calls Linear.

## Grammar

```
Refs-line  := "Refs:" SP token *( ( SP | "," ) token )
token      := spec | spec-task | bug | campaign | linear | "NO-CODE"
spec       := 3 digits                       ; specs/NNN-* must exist
spec-task  := 3 digits "/T" 3 digits         ; specs/NNN-* must exist
bug        := "BUG-" 3 digits                ; must be a "### BUG-###:" heading in docs/testing/bug-log.md
campaign   := ("HANDOFF-" | "DEPLOY-" | "Q-") 2 digits
linear     := "JOR-" 1*digit                 ; NEW: format only
```

Rules:

- The `Refs:` line is in the message's trailer block (last paragraph, next to the attribution line).
- `NO-CODE` must stand alone.
- Merge, `fixup!`, `squash!`, `amend!` and `Revert "` commits are exempt.
- Tokens combine freely: `Refs: 014 JOR-12 BUG-020`.
- A Linear key is optional; the line must still contain at least one valid token.

## Cases (added to `.githooks/tests/commit-msg-cases.sh`)

| Message trailer | Verdict |
|---|---|
| `Refs: JOR-12` | pass |
| `Refs: 014 JOR-12` | pass |
| `Refs: JOR-12, JOR-340 BUG-001` | pass |
| `Refs: NO-CODE JOR-12` | block (NO-CODE alone) |
| `Refs: JOR-` | block |
| `Refs: JOR-1x` | block |
| `Refs: jor-12` | block (uppercase only) |
| `Refs: TICKET-12` | block |

A malformed key fails with the existing message plus the accepted-token list including `JOR-###`.

## Not covered

Whether the issue exists in Linear; whether the key is for team `JOR` is implied by the prefix only.
