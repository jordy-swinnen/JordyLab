# Visual review: 016 app redesign and themes

**Status**: automated evidence recorded; the owner approved the design on 2026-11-02 (HANDOFF-39). The step counts (SC-007) were not recorded.

## What was checked, and how

| Check | Result | Evidence |
|---|---|---|
| Text contrast, six themes (text, text-2, text-3 on ground, rail, surface; accent, status, mark and plate pairs; mark tints over ground, rail, surface) | pass | `libs/shared/theme/api/src/lib/theme-contrast.spec.ts` |
| Axe (WCAG 2 A/AA) on every signed-in page, default dark and light, desktop and 390 px | pass | `accessibility.spec.ts`, e2e run of 2026-11-02 (217 of 217) |
| Axe on the Library and a game detail in all six themes, desktop and phone | pass | same |
| No sideways scroll at 320, 360, 390, 430 px; no control under 44 px on a phone | pass | `layout.spec.ts`, `edge-cases.spec.ts` |
| Loading, error and first-run states; reduced motion stops the placeholder pulse | pass | `states.spec.ts`, unit specs |
| Forced-colours mode, blocked font file | pass | `edge-cases.spec.ts` |
| Theme choice, persistence, follow-the-device, no flash, guest access | pass | `theme.spec.ts` |
| Production build boots; Content-Security-Policy | pass | `bun tools/app-boot-check.mjs`, `csp.spec.ts` |
| Library on desktop and a 375 px phone in Content Led (error state, no backend running) viewed in the browser pane | looks as designed | session, 2026-11-02 |

Contrast findings fixed on the way: text-3 and the mark colours were darkened or lightened in the light themes and text-3 in Content Led (see `themes.js`) after axe measured chips on the rail and line surfaces.

## Not done

- Step counts to find a game and set a mark before and after (SC-007): the owner approved the design without counting.
- Status bar on a light theme on a real Android device and the sign-in page following the device (HANDOFF-38).

## Differences agreed with the owner

None: the owner approved the design as built on 2026-11-02, with no changes asked for.

## Known deviations from the task list

- Portfolio holdings stay a scrollable table on a phone instead of turning into rows (duplicated markup would break the `row-price` and `row-value` hooks).
- The fingerprint switch in `libs/shared/auth` keeps its own markup (same dimensions as the shared Switch) because that library is tagged `type:api` and may not import the `type:ui` layout library.
- The game detail page keeps its banner with the overlapping cover instead of the cover-above-title layout.
- LibBot derives its five states inside the view component rather than in the container.
