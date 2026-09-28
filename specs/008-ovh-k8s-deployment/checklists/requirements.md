# Specification Quality Checklist: Production Deployment on Self-Managed k3s (OVH VPS)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-28
**Feature**: [spec.md](../spec.md)

## Content Quality

- [ ] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [ ] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [ ] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous (aside from the marked items)
- [x] Success criteria are measurable
- [ ] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [ ] No implementation details leak into specification

## Notes

- **The three unchecked "implementation detail" / "non-technical" / "technology-agnostic" items are an accepted,
  deliberate exception**, not an oversight. This feature *is* an infrastructure deployment, and the specific
  technology choices (self-managed k3s vs. OVH Managed Kubernetes, k3s's bundled Traefik/ServiceLB/local-path,
  CloudNativePG, SOPS + age, GHCR, GitHub Actions) are themselves the already-researched, already-decided scope of
  the feature — see `specs/_drafts/008-deployment/research.md` — not incidental implementation choices to defer to
  planning. Deeper implementation detail (directory layout, exact Helm/Kustomize values, Spring profile wiring) was
  intentionally left out of this spec and belongs in `specs/_drafts/008-deployment/plan-draft.md` for
  `/speckit-plan`. The sole "user" of this feature is Jordy himself operating his own infrastructure, so
  "non-technical stakeholders" does not apply in the usual sense.
- **`/speckit-clarify` ran on 2026-09-28** and resolved 5 of the original 6 open questions from `research.md`: CI's
  path to the k3s API (WireGuard/Tailscale, not a public 6443), ntfy self-hosted in-cluster, backup retention
  (7 daily + 4 weekly), the VPS commitment term (monthly, not 12-month upfront), and the restore-drill cadence
  (quarterly). A 6th marker (confirming the deploy trigger) turned out to already be fully answered by User Story
  4's acceptance scenarios and was resolved directly without spending a question slot.
- **One [NEEDS CLARIFICATION] marker remains, deliberately deferred**: which OVH datacenter/region to provision the
  VPS and Object Storage bucket in. It has near-zero architectural impact — any EU location works identically, and
  Jordy picks it at OVH checkout — so it's left as a runbook-time decision rather than spent as one of the 5
  `/speckit-clarify` question slots.
- **Ready for `/speckit-plan`** — no remaining marker blocks architecture or task decomposition.
