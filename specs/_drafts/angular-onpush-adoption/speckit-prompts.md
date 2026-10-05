# Adopt OnPush Change Detection: SpecKit Prompts

Background is in `research.md`, the expected spec is in `spec-draft.md`.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.

## `/speckit-specify`

```
Short name: angular-onpush-adoption.

Move the Angular components that the Angular 22 upgrade pinned to ChangeDetectionStrategy.Eager to OnPush, convert any plain-field state shown in templates to signals, and turn the prefer-on-push lint rule back on in every project. No user-visible behaviour change.

Out of scope: zone.js, SSR, changing UI libraries.
```
