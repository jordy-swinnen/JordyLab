# Contract: theme tokens

The repository's authoritative copy of the six themes. The design system artifact referenced from JOR-55 is the source it was taken from; after this file is merged, **this file wins** and the design system is updated to match.

## How a token reaches the screen

Every theme is one block of CSS custom properties selected by a `data-theme` attribute on the root element. All six blocks declare the **same names**; a screen never knows which theme is active. The default block also answers to `:root`, so a page with no attribute (the dev harness apps, a failed boot script) renders Content Led.

Existing variable names are kept as the stable contract because the shared component library and the helm components read them. New names are added only where the design needs something the old set cannot express.

| Design token | CSS variable | Notes |
|---|---|---|
| `ground` | `--background` | page ground |
| `rail` | `--rail` | existing; sidebar and tab bar ground |
| `surface` | `--card`, `--popover` | same value for both |
| `line` | `--border`, `--secondary`, `--muted`, `--accent` | hairlines, skeleton fill, hover and quiet fills (these four are one value) |
| `line-strong` | `--input` | control borders and cover frames |
| `text` | `--foreground`, `--card-foreground`, `--popover-foreground`, `--accent-foreground` | |
| `text-2` | `--secondary-foreground` | |
| `text-3` | `--muted-foreground` | |
| `accent` | `--primary`, `--ring` | |
| `accent-ink` | `--primary-foreground` | |
| `accent-text` | `--accent-text` | **new** |
| `installed` | `--installed` | **new** |
| `mark-want`, `mark-liked`, `mark-disliked` | `--mark-want`, `--mark-liked`, `--mark-disliked` | **new** |
| `plate-1` … `plate-6` and `plate-N-ink` | `--plate-1` … `--plate-6`, `--plate-1-ink` … `--plate-6-ink` | **new** |
| `series-1` … `series-4` | `--series-1` … `--series-4` | **new**; replaces `iris` and `deep` |
| `platform-*`, `rom-validated(-ink)` | `--platform-*`, `--rom-validated(-ink)` | **new**; identical in every theme |
| (none) | `--destructive`, `--destructive-foreground` | kept; per theme, see "Destructive" below |

Variables are written as HSL triplets (`255 25% 6%`, no `hsl()` wrapper) exactly like today, so Tailwind's `hsl(var(--x) / <alpha-value>)` opacity utilities keep working. The hex values below are converted at authoring time; a unit test converts back and compares, so rounding drift is caught.

`--iris` and `--deep` are removed; their three uses move to `--series-2` and `--primary`. `--radius` stays `0.75rem`.

## Per theme

Each block also sets `color-scheme: dark` or `color-scheme: light`, so native scrollbars, form controls and the text-selection colour follow the theme.

### Destructive

Today `--destructive` is one red. The design shows errors in the theme's warm mark colour (`mark-liked`). **Decision**: `--destructive` equals `mark-liked` in each theme, so error boxes and destructive buttons are legible in all six and the contrast table below covers it.

## Contrast contract

For every theme: `text`, `text-2` and `text-3` on `ground`, `rail` and `surface` meet 4.5:1 (`text-3` on `line` fill meets 4.5:1 where it is used for placeholders); `accent-ink` on `accent` meets 4.5:1; `installed`, the three mark colours and `accent-text` on `surface` meet 4.5:1; `plate-N-ink` on `plate-N` meets 4.5:1; platform inks on their fills meet 4.5:1. `line` and `line-strong` are decorative and are not required to meet 3:1, so no control may rely on a border alone (spec FR-006, FR-007). A unit test computes all of these from this table.

## Values

| Token | Content Led | Oxblood | Flare Orange | Paper | Glasshouse | Heather |
|---|---|---|---|---|---|---|
| `ground` | `#131314` | `#140d0e` | `#0d0b13` | `#f2eee5` | `#eef3ee` | `#f0eef5` |
| `rail` | `#0f0f10` | `#100a0b` | `#0b0a10` | `#e9e4d8` | `#e3ebe3` | `#e6e3ee` |
| `surface` | `#1b1b1d` | `#1d1315` | `#161320` | `#faf7f1` | `#f8fbf7` | `#faf9fd` |
| `line` | `#2c2c2f` | `#36242a` | `#2a2537` | `#d8d1c0` | `#cfdccf` | `#d9d4e6` |
| `line-strong` | `#424246` | `#4d3339` | `#3b354b` | `#b9b19e` | `#aabfaa` | `#b4abcb` |
| `text` | `#f1f0ec` | `#f0e7de` | `#f2ede3` | `#1b1a1f` | `#14231a` | `#1d1830` |
| `text-2` | `#b0afab` | `#bcaaa4` | `#a8a1b5` | `#4a4952` | `#3d5245` | `#4a4562` |
| `text-3` | `#92918d` | `#9d8c87` | `#918a9e` | `#67656c` | `#566a5d` | `#625d7a` |
| `accent` | `#ece8dd` | `#ee5a70` | `#ff6a3d` | `#2a43d6` | `#1d6b4a` | `#7a2e8e` |
| `accent-ink` | `#141414` | `#2a0509` | `#1b0a04` | `#ffffff` | `#ffffff` | `#ffffff` |
| `accent-text` | `#f1f0ec` | `#ee5a70` | `#ff6a3d` | `#2a43d6` | `#1d6b4a` | `#7a2e8e` |
| `installed` | `#8fd9b0` | `#6fd3a0` | `#2dd4bf` | `#1f7a4d` | `#1d6b4a` | `#1d7a52` |
| `mark-want` | `#9db4ff` | `#b9a8ff` | `#a99bff` | `#3f4fd0` | `#3a56b8` | `#3f4fd0` |
| `mark-liked` | `#ff9fb8` | `#f0c36a` | `#ff8fb1` | `#b4234f` | `#a32a58` | `#a8284f` |
| `mark-disliked` | `#a3a3a8` | `#a89c98` | `#98a2b3` | `#5b6172` | `#566070` | `#5b5f70` |
| `plate-1` | `#2e6f95` | `#3a1620` | `#2b2650` | `#2a43d6` | `#1d6b4a` | `#7a2e8e` |
| `plate-1-ink` | `#eaf2f6` | `#f0e7de` | `#f2ede4` | `#ffffff` | `#f8fbf7` | `#faf9fd` |
| `plate-2` | `#d9c9a3` | `#f0e7de` | `#f2ede4` | `#1b1a1f` | `#14231a` | `#1d1830` |
| `plate-2-ink` | `#201a10` | `#2a0509` | `#1c0a04` | `#f2eee5` | `#eef3ee` | `#f0eef5` |
| `plate-3` | `#b23a48` | `#ee5a70` | `#9a8bff` | `#e8d9b5` | `#cfe3d2` | `#dcd0ee` |
| `plate-3-ink` | `#f8e9ea` | `#2a0509` | `#1c0a04` | `#1b1a1f` | `#14231a` | `#1d1830` |
| `plate-4` | `#5a8f4e` | `#5c2430` | `#6e2a17` | `#d7e0ff` | `#e6d9b8` | `#e9d3da` |
| `plate-4-ink` | `#0f1d0c` | `#f0e7de` | `#f2ede4` | `#1b2a8a` | `#14231a` | `#1d1830` |
| `plate-5` | `#e9b44c` | `#241518` | `#241f38` | `#1f7a4d` | `#3a56b8` | `#3f4fd0` |
| `plate-5-ink` | `#2b1d00` | `#ee5a70` | `#ff6a3d` | `#f2eee5` | `#f8fbf7` | `#faf9fd` |
| `plate-6` | `#3b3550` | `#b9a39a` | `#ff6a3d` | `#e9e2d2` | `#d7dfeb` | `#cdd6e8` |
| `plate-6-ink` | `#e6e6ea` | `#140d0e` | `#1c0a04` | `#1b1a1f` | `#14231a` | `#1d1830` |
| `series-1` | `#ece8dd` | `#ee5a70` | `#ff6a3d` | `#2a43d6` | `#1d6b4a` | `#7a2e8e` |
| `series-2` | `#9db4ff` | `#b9a8ff` | `#a99bff` | `#b4234f` | `#3a56b8` | `#3f4fd0` |
| `series-3` | `#8fd9b0` | `#6fd3a0` | `#2dd4bf` | `#1f7a4d` | `#a32a58` | `#1d7a52` |
| `series-4` | `#e9b44c` | `#f0c36a` | `#f2ede3` | `#1b1a1f` | `#14231a` | `#1d1830` |
| `platform-steam` | `#1b2838` | `#1b2838` | `#1b2838` | `#1b2838` | `#1b2838` | `#1b2838` |
| `platform-steam-ink` | `#66c0f4` | `#66c0f4` | `#66c0f4` | `#66c0f4` | `#66c0f4` | `#66c0f4` |
| `platform-ps` | `#0070d1` | `#0070d1` | `#0070d1` | `#0070d1` | `#0070d1` | `#0070d1` |
| `platform-ps-ink` | `#ffffff` | `#ffffff` | `#ffffff` | `#ffffff` | `#ffffff` | `#ffffff` |
| `platform-snes` | `#e60012` | `#e60012` | `#e60012` | `#e60012` | `#e60012` | `#e60012` |
| `platform-snes-ink` | `#ffffff` | `#ffffff` | `#ffffff` | `#ffffff` | `#ffffff` | `#ffffff` |
| `rom-validated` | `#9be564` | `#9be564` | `#9be564` | `#9be564` | `#9be564` | `#9be564` |
| `rom-validated-ink` | `#0b1a05` | `#0b1a05` | `#0b1a05` | `#0b1a05` | `#0b1a05` | `#0b1a05` |
