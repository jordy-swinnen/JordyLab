# Contract: shared UI pieces

Presentational components for what is repeated by hand on every screen today. All are standalone, take inputs only, hold no state and live in a new shared UI library, `libs/shared/layout` (`scope:shared`, `type:ui`), exported from its `index.ts`. Containers keep fetching and mapping; these only draw.

| Component | Inputs | Replaces |
|---|---|---|
| `PageHeaderComponent` | `title`, optional `subtitle`; content slot for right-aligned actions | the title block copied into every page |
| `EmptyStateComponent` | `title`, `hint`, optional action label and `actionKind` (`primary` or `secondary`); `actionClicked` output | dashed empty boxes in eight screens |
| `ErrorStateComponent` | `message`; `retry` output | the destructive box repeated in eight places |
| `SkeletonRowsComponent` | `count`, `height` (defaults 4 and 84) | ad-hoc skeleton lists |
| `SwitchComponent` | `checked`, `label`; `checkedChange` output; real `role="switch"` | the two hand-built switches |
| `SpecRowComponent` | `label`, value in the slot | the key/value row copied about ten times |
| `TabBarComponent` | list of `{ label, icon, route, active }` | the phone nav row |

## Rules

- Labels and copy come from the caller; the components contain no product wording except the "Try again" button text.
- Each component uses only theme variables and the shared type classes; no hex values.
- Each has a spec using Spectator, with `useValue` plain objects where a dependency is needed, and an axe check in the end-to-end suite through the pages that use it.
- The existing spartan `hlm-skeleton`, `hlmBtn`, `hlmInput` and `hlmBadge` directives stay the base for skeleton blocks, buttons, inputs and badges; the new components compose them.

## Screen states

A screen container that loads data exposes one `state` value for its view: `loading`, `empty`, `error` or `loaded` (LibBot adds `firstRun`, `answering`, `limit`). The view switches on it; it does not infer state from several flags.
