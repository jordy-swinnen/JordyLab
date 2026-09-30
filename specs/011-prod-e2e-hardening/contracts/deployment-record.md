# Contract: Deployment Record

Stated in chat **before** approving, then stored in the test plan's Deployments section.

```markdown
#### DEPLOY-<nn>
- PR: <url>  | Branch: fix/e2e-<topic>
- Deploy path: sha (today's `deploy-prod.yml`) | version (after the release flow ships)
- Merged SHA: <full sha>  | Release version: vX.Y.Z (version path only)
- Previous good: sha-<full sha> | vX.Y.Z
- Bugs: BUG-..., BUG-...
- Build run: <url> — green
- Contains Flyway migration: yes/no
- Contains Keycloak realm change: yes/no
- Contains secret/config change: yes/no
- Decision: auto-approve (all three "no") | PAUSED for Jordy
- Approval: <time> via GitHub API | refused: <exact error> → waiting on Jordy
- Rollout: backend / frontend / keycloak — <status>
- Running image tag: sha-<sha> | vX.Y.Z (matches: yes/no)
- Prod re-verification: repro steps <PASS/FAIL>, smoke suite <PASS/FAIL>
- Outcome: verified | rolled back to <sha or version> (S1 incident BUG-nnn)
```

Approval preconditions (all required): commit was merged by this campaign; `Build` green; no other deployment
in flight; all three sensitive flags "no". Never approve a pending deployment that does not match `Merged SHA` (or, on the version path, the release whose
tag points at `Merged SHA`).

Rollback commands:
- sha path: `gh workflow run deploy-prod.yml -f sha=<previous-good-sha>`
- version path: `gh workflow run deploy-prod.yml -f version=<previous-good-version>` (*Run workflow* in the UI)

Never change environment protection rules, reviewers or secrets.
