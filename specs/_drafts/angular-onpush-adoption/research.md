# Adopt OnPush Change Detection: Research

Date: 2026-10-05. Found during the Angular 22 upgrade.

- The Angular 22 migration `change-detection-eager` added `changeDetection: ChangeDetectionStrategy.Eager` to every component that
  did not set a strategy: 38 lint findings appeared once `angular-eslint` 22 enabled `prefer-on-push-component-change-detection`.
- The rule is switched off in each Angular project's `eslint.config.mjs` with a comment pointing here.
- The app is zoneless and signal-based, so most components should already be safe, but plain fields mutated after async work are the
  risk; unit tests alone may not show a stale view.

## Open questions

- All at once or per library? Does an end-to-end suite come first?
