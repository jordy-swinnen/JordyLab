# Quickstart: Continuous Deploy on Green Main

**Feature**: 019 | **Spec**: [spec.md](spec.md) | **Contracts**: [contracts/](contracts/)

Validation guide for the feature, in the order it must run. Steps Q5 and Q6 touch production and need Jordy's go-ahead
before they start.

## Q0. Before the first run (owner)

1. GitHub → Settings → Environments → `production`: **remove the required reviewer** (FR-002). Keep the environment and
   its secrets. Until this is done, automatic deploys wait for an approval that nothing asks for.
2. Repository variable `AUTO_DEPLOY` is unset or `false`. It is set to `true` only after Q2 passes (D11).
3. Read-only cluster access for the checks below: `export KUBECONFIG=~/.kube/jordylab.yaml`.

## Q1. Local checks (no cluster)

```bash
bash deploy/scripts/tests/deploy-decision-cases.sh      # decision: refused / superseded / proceed
bash deploy/scripts/tests/backup-freshness-cases.sh     # backup: fresh / stale / none
actionlint .github/workflows/*.yml                      # install first: brew install actionlint
cd jordylab-be && ./gradlew test --tests '*ApplicationInfo*' --no-daemon   # only the changed test
```

**Expected**: every case passes; `actionlint` reports nothing. The `deploy-scripts` job in `build.yml` runs the two script
suites on the PR as well.

## Q2. Dry run on production (nothing changes)

Needs the implementation merged to `main` (GitHub dispatches only the default branch's copy of a workflow).

```bash
SHA=$(git rev-parse origin/main)
gh workflow run deploy-prod.yml --repo jordy-swinnen/JordyLab -f target="$SHA" -f dry_run=true
gh run watch
```

**Expected**: the summary shows `dry-run-ok`, the previous live commit (from the current backend image tag), the newest
completed backup and its age, and the three smoke checks passing. `kubectl -n jordylab get deploy backend -o jsonpath='{.spec.template.spec.containers[0].image}'` is unchanged.

Negative cases (each must end `refused` before the cluster is read):

```bash
gh workflow run deploy-prod.yml -f target=v9.9.9 -f dry_run=true
gh workflow run deploy-prod.yml -f target=0000000000000000000000000000000000000000 -f dry_run=true
```

If the backup line reads "none" or a warning, check Q7 before continuing.

## Q3. First automatic deploy

1. Set the repository variable `AUTO_DEPLOY` to `true`.
2. Merge a small change to `main` (a docs-only change is fine; note that it still restarts the pods, D13).
3. Watch: the `Build` run's `deploy` job starts after `build-and-push`, with **no approval prompt**.

Verify, after the run is green:

```bash
kubectl -n jordylab get deploy backend frontend keycloak \
  -o jsonpath='{range .items[*]}{.metadata.name} {.spec.template.spec.containers[0].image}{"\n"}{end}'
# every image ends with sha-<the merge commit>

kubectl -n jordylab port-forward deploy/backend 18080:8080 >/dev/null 2>&1 & PF=$!
sleep 3; curl -s localhost:18080/actuator/info; echo; kill $PF
# {"commit":"<the merge commit>"}          (SC-006)
```

Record in `docs/runbook.md` §10:
- merge time → live time (SC-001), from the Build run's start to the deploy job's end;
- the backend outage (old pod stopped → new pod ready), from the rollout log;
- whether users stayed signed in: sign in on `https://jordylab.be`, note the state, deploy, reload (spec assumption on sessions).

## Q4. Ordering and the superseded skip

Push two small commits to `main` a few minutes apart (or merge two PRs back to back).

**Expected**: the second deploy queues behind the first (GitHub shows *Waiting*), both runs are kept (`queue: max`), and when
the older commit's turn comes after a newer one is live, its run ends green with a `superseded` notice and no apply.
Check the run list: `gh run list --workflow build.yml --limit 6`.

## Q5. Broken-image drill (needs Jordy's go-ahead; quiet time, not 03:00–05:00 UTC or the 1st of the month)

1. In a PR, change one line in `deploy/k8s/base/backend.yaml`: the readiness probe path to
   `/actuator/health/does-not-exist`. Merge it.
2. Expect the deploy run to: apply, time out the backend rollout after 8 minutes, roll back to the previous commit
   (its images **and** manifests), wait for its rollouts, pass the smoke checks, and end red with `rolled-back`.
3. Expect a GitHub failure notice (FR-011).
4. Measure: deploy start → previous commit serving again. Must be **within 15 minutes** (SC-002).
5. Then revert the bad commit on `main` and merge the revert. The rollback is temporary: until the revert lands, the next
   green merge would deploy the newest commit again (spec edge case).

## Q6. Manual rollback and redeploy

```bash
gh workflow run deploy-prod.yml -f target=<previous good sha>
```

**Expected**: outcome `live`; only that commit's images and manifests change (SC-003). A release tag is accepted the same
way: `gh workflow run deploy-prod.yml -f target=v0.0.1-rc33` (do not use it casually: it reverts the whole app).

## Q7. Backup object check (once, before Q2 counts as passed)

```bash
kubectl -n jordylab get backups.postgresql.cnpg.io \
  -o jsonpath='{range .items[*]}{.metadata.name} {.status.phase} {.status.stoppedAt}{"\n"}{end}'
```

**Expected**: the daily backup shows phase `completed` with a `stoppedAt` in the last 26 hours. If the phase reads
something else, fix `backup-freshness.sh` (D9) before relying on the warning.

## Q8. The APK is no longer built by merges

After a week of merges:

```bash
gh run list --workflow apk-release.yml --limit 10     # expect no runs started by a push
gh workflow run apk-release.yml -f tag=v9.9.9          # expect the verify job to fail: not a tag on main
```

**Expected**: zero APK runs from merges (SC-005). The manual input is validated before any build.

## Q9. Docs and agents describe the new flow

```bash
grep -rn -i "approve the production\|git tag v0\|reviewer Jordy\|release.yml" \
  docs/runbook.md AGENTS.md .claude/skills/jordylab-ops/SKILL.md .claude/agents docs/learn/10-cicd.md
```

**Expected**: no match that tells the reader to tag a release to go live or to approve a deploy. Historical mentions in
`specs/0xx-*` and `docs/testing/` are allowed. Also check `.claude/skills/flyway-migration/SKILL.md` names the expand and
contract rule, and the `code-reviewer` checklist has the migration item (FR-019).

## Q10. Drill record

Log in `docs/runbook.md` (§10 or §20): the date, the commit that was broken, the rollback time, the manual rollback
result, and the merge-to-live times of the first five automatic deploys (FR-026).
