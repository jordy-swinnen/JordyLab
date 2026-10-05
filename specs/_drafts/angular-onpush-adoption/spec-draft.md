# Feature Specification: Adopt OnPush Change Detection

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-10-05
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Module**: `jordylab-fe` (all Angular libraries and apps)

---

## Overview

Angular 22 made OnPush the default. The upgrade migration pinned every existing component that did not opt in to
`ChangeDetectionStrategy.Eager` so behaviour stayed identical, and the recommended lint rule against opting out was switched
off in each project. This feature moves those components to OnPush deliberately and turns the rule back on.

---

## User Scenarios & Testing

### User Story: Components refresh only when they need to (Priority: Medium)

As the developer, I want components to use OnPush with signals, so that the app does less work per change and follows the
framework default.

**Independent Test**: after the change, no component pins `Eager`, the lint rule is back on, all unit tests pass and the main
journeys (sign-in, catalog grid and detail, chat, briefing, settings) behave as before.

### User Story: No screen goes stale (Priority: High)

As the developer, I want every screen that updates after async work to keep updating, so that no component silently stops refreshing.

**Independent Test**: a test or journey per component that changes state after an HTTP call or timer confirms the view updates.

---

## Requirements

- Every component pinned to `Eager` MUST be moved to OnPush or kept `Eager` with a written reason.
- State shown in templates MUST come from signals or inputs; plain fields mutated after async work MUST be converted.
- The `prefer-on-push-component-change-detection` lint rule MUST be enabled again in every Angular project config.
- No user-visible behaviour MAY change.

## Out of Scope

Zone.js, SSR, changing UI libraries.

## Clarifications Needed

- Convert all components at once, or one library per change?
- Is an automated end-to-end journey suite available first, to catch stale screens?
