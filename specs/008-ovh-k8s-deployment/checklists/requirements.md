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
- **6 [NEEDS CLARIFICATION] markers remain by explicit instruction**, exceeding the standard 3-marker limit: the
  user asked to carry forward every "Open question for `/speckit-clarify`" from `research.md` as a marker rather
  than have them guessed or resolved inline during `/speckit-specify`. They are: (1) how CI reaches the k3s API to
  deploy, (2) ntfy in-cluster vs. ntfy.sh, (3) backup retention schedule, (4) confirming the deploy trigger, (5) VPS
  commitment term, (6) VPS/Object Storage datacenter region. All 6 are resolved with concrete recommendations in
  `specs/_drafts/008-deployment/speckit-prompts.md` §2 for `/speckit-clarify` to apply.
- **Next step is `/speckit-clarify`, not `/speckit-plan`** — the 6 markers above should be resolved there before
  planning.
