# Contract: Bug Log Entry (`docs/testing/bug-log.md`)

Append-only. One entry per defect. Secrets redacted as `<redacted>`.

```markdown
### BUG-<nnn>: <short title>
- Status: OPEN | FIXING | FIXED-LOCAL | DEPLOYED | VERIFIED-PROD | BLOCKED | WONTFIX-QUESTION
- Severity: S1 (prod unusable / data loss / security) | S2 (core feature broken) | S3 (degraded, workaround exists) | S4 (cosmetic)
- Area/spec: <module> / <spec id>          e.g. gamecatalog / 004
- Env found: local | prod | both
- Coverage rows: <rowId>, ...
- Steps to reproduce:
  1. ...
- Expected (cite spec/story): <spec>#<US/FR> — "<quote>"
- Actual (logs/screenshot, secrets redacted):
- Root cause:
- Fix (PR / commit / tag):
- Regression test added: <path> | none because <reason>
- Verified on prod: <YYYY-MM-DD> — <how: URL, request, screenshot ref>
```

Rules:
- `nnn` is zero-padded, sequential, never reused.
- `Coverage rows` is an addition to the brief's format (links bug ↔ matrix); all brief fields are kept in order.
- Baseline suite failures use `Env found: local` and cite the failing test name.
- S1 incident entries (rollback) add `- Incident: rolled back from sha-<bad> to sha-<good> at <time>`.
