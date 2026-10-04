# Specification Quality Checklist: Mobile App (Android via Capacitor, iOS Home-Screen Web App)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-28
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- The one [NEEDS CLARIFICATION] marker deliberately left by `/speckit-specify` (the push-notification
  delivery channel, User Story 6 / FR-016) was resolved in the 2026-09-27 (session-dated 2026-09-28)
  `/speckit-clarify` round: **Ntfy + deep links**, reusing the existing server rather than adding Firebase
  Cloud Messaging or a custom UnifiedPush plugin. Two further decisions were made in the same round and are
  recorded under `## Clarifications` in spec.md: "Save to FNA" stays in scope at P3 (FR-017), and the app's
  minimum supported OS is Android 10 / API 29 (FR-018), raised from Capacitor's default floor of API 24 to
  shrink the test matrix.
- "Capacitor" (Android shell) and "home-screen web app" (iOS) are retained as product/distribution
  decisions taken directly from the user's feature description — the request is specifically about *this*
  distribution model (sideloaded APK, no Play Store, no native iOS build), not an internal implementation
  choice. Specific plugins, libraries, and code structure (e.g. which biometric or share-target plugin,
  the Keycloak adapter approach, Nx app layout) are deliberately left out of the spec and live in
  `specs/_drafts/mobile-app/research.md` and the plan artifacts.
- Depends on spec 006 (admin/guest roles, approval/revocation) and on spec 008, a public HTTPS production
  deployment that does not yet exist — recorded under Assumptions and in the header's **Depends On** line.
  007 can be specified and planned now but cannot be validated end-to-end on a real device until 008 ships.
- Scope explicitly excludes Play Store/F-Droid publishing, a native iOS app, TestFlight, over-the-air
  web-bundle updates, offline mode, tablet/foldable-specific layouts, and notifications for guests (all
  carried over verbatim from the user's feature description).
