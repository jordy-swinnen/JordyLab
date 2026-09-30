# Quickstart: Running and Validating the Campaign

How to run each phase and how to tell it worked. Formats live in [contracts/](contracts/); entities in
[data-model.md](data-model.md).

## Prerequisites (pre-flight, all read-only)

```bash
gh auth status
```
```bash
tailscale status
```
```bash
KUBECONFIG=~/.kube/jordylab.yaml kubectl -n jordylab get pods
```
```bash
gitleaks version
```

Expected: `gh` logged in with `repo` + `workflow` scopes; `jordylab-vps` online; pods listed. If `kubectl` fails,
continue without cluster access and log it as a limitation (research R2).

## Phase 0 — Recon (validates US1)

1. Build the coverage matrix from `specs/001…010` per [contracts/coverage-matrix.md](contracts/coverage-matrix.md).
2. Baseline suites (research R4), one run each:
   ```bash
   cd jordylab-be && ./gradlew build
   ```
   ```bash
   cd jordylab-fe && bunx nx run-many -t test,lint
   ```
   ```bash
   cd gamecatalog-scanner && python3 -m venv .venv && .venv/bin/pip install -e ".[dev]" && .venv/bin/python -m pytest --cov=src
   ```
   ```bash
   gitleaks detect --redact --no-banner
   ```
3. Log missing-story bugs for 006 US4–US7 and open 009 stories (FR-012a); mark 010 + garmin sidecar NOT BUILT.
4. Send HANDOFF batch 1 (research R10).

**Done when**: `docs/testing/e2e-test-plan.md` and `docs/testing/bug-log.md` exist; every row has a status or `TODO`;
the plan summary + contradictions are shown to Jordy; **no code changed**. Wait for approval.

## Phase 1 — Test (validates US2, US3, US5)

- Run [contracts/smoke-suite.md](contracts/smoke-suite.md) A1–A13, B1–B3 against `https://jordylab.be`. Example
  read-only checks:
  ```bash
  curl -sI http://jordylab.be/ | head -3
  ```
  ```bash
  curl -s https://jordylab.be/auth/realms/jordylab/.well-known/openid-configuration | jq -r .issuer
  ```
  ```bash
  curl -s -o /dev/null -w '%{http_code}\n' https://jordylab.be/api/gamecatalog/games
  ```
  Expected: redirect to https; issuer `https://jordylab.be/auth/realms/jordylab`; `401`.
- Local stack for mutating tests: `preview_start` `jordylab-be` and `jordylab-fe` (`.claude/launch.json`), after
  `podman compose -f jordylab-be/compose.yaml up -d`.
- Areas C–G per spec US3; every AI call gets a tally row.

**Done when**: every matrix row is PASS / FAIL (+BUG) / BLOCKED / NOT TESTABLE with reason; handoffs are `verified`
or `failed` with a logged reason.

## Phase 2 — Fix (validates US4 part 1)

Per batch: branch `fix/e2e-<topic>` from fresh `main` → failing test → fix → relevant tests only (+
`/modularity-check` if structure changed) → local real-flow check → `gitleaks detect --redact` → PR → wait for
`Build` + `claude-pr-review` → merge.

**Done when**: bug is `FIXED-LOCAL` with a regression test path (or a reason).

## Phase 3 — Deploy & verify (validates US4 part 2)

1. Write the [deployment record](contracts/deployment-record.md); pause if any sensitive flag is `yes`.
2. Find and approve the pending deployment (research R8):
   ```bash
   gh run list --workflow deploy-prod.yml --limit 3
   ```
3. Watch rollout and check running tags:
   ```bash
   KUBECONFIG=~/.kube/jordylab.yaml kubectl -n jordylab get deploy -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.template.spec.containers[0].image}{"\n"}{end}'
   ```
4. Re-run the bug's repro steps and the smoke suite on prod.
5. On regression:
   ```bash
   gh workflow run deploy-prod.yml -f sha=<previous-good-sha>
   ```
   After the release flow (T040/T041) ships, roll back by version instead:
   ```bash
   gh workflow run deploy-prod.yml -f version=<previous-good-version>
   ```
   then log an S1 incident and stop.

**Done when**: bug is `VERIFIED-PROD` with prod evidence, or rolled back and escalated.

## Close-out (validates SC-001…SC-010)

- No `TODO` rows; all S1/S2 `VERIFIED-PROD` (or deferred with Jordy's recorded agreement).
- Final smoke pass green after the last deploy.
- AI tally ≤ 30 per full pass.
- PR with `docs/testing/*`; final report per FR-026.
- Last: `docs/testing/manual-test-runbook.md` has one procedure per remaining NOT TESTABLE/BLOCKED row (SC-010),
  linked from `docs/runbook.md`, pushed to the same PR.
