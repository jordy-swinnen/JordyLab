/**
 * The design tokens and component classes for every app (jordylab, fna, gamecatalog). Each app's tailwind.config.js pulls in
 * `extend` and `plugin`; each app's styles.css only holds the @tailwind directives.
 *
 * Colours are authored once, as hex, in themes.js. This plugin turns each theme into a block of CSS variables selected by the
 * `data-theme` attribute (the default theme also answers to :root), written as HSL triplets so Tailwind's opacity utilities
 * keep working. Variable names and their meaning: specs/014-app-redesign-themes/contracts/theme-tokens.md.
 */
const plugin = require('tailwindcss/plugin');
const { defaultDarkThemeId, themes } = require('./themes.js');

/** The names the spartan components and the existing templates read, mapped to the design tokens that feed them. */
const SHARED_VARIABLES = {
  '--background': 'ground',
  '--rail': 'rail',
  '--card': 'surface',
  '--card-foreground': 'text',
  '--popover': 'surface',
  '--popover-foreground': 'text',
  '--foreground': 'text',
  '--border': 'line',
  '--secondary': 'line',
  '--muted': 'line',
  '--accent': 'line',
  '--input': 'line-strong',
  '--accent-foreground': 'text',
  '--secondary-foreground': 'text-2',
  '--muted-foreground': 'text-3',
  '--primary': 'accent',
  '--ring': 'accent',
  '--primary-foreground': 'accent-ink',
  '--destructive': 'mark-liked',
  '--destructive-foreground': 'ground',
};

/** Tokens that have no spartan equivalent are exposed under their own name. */
const OWN_NAME_TOKENS = [
  'accent-text',
  'installed',
  'mark-want',
  'mark-liked',
  'mark-disliked',
  'platform-steam',
  'platform-steam-ink',
  'platform-ps',
  'platform-ps-ink',
  'platform-snes',
  'platform-snes-ink',
  'rom-validated',
  'rom-validated-ink',
  ...[1, 2, 3, 4, 5, 6].flatMap((n) => [`plate-${n}`, `plate-${n}-ink`]),
  ...[1, 2, 3, 4].map((n) => `series-${n}`),
];

function hexToHslTriplet(hex) {
  const red = parseInt(hex.slice(1, 3), 16) / 255;
  const green = parseInt(hex.slice(3, 5), 16) / 255;
  const blue = parseInt(hex.slice(5, 7), 16) / 255;
  const maximum = Math.max(red, green, blue);
  const minimum = Math.min(red, green, blue);
  const lightness = (maximum + minimum) / 2;
  const delta = maximum - minimum;
  if (delta === 0) {
    return `0 0% ${round(lightness * 100)}%`;
  }
  const saturation = delta / (1 - Math.abs(2 * lightness - 1));
  let hue;
  if (maximum === red) {
    hue = ((green - blue) / delta) % 6;
  } else if (maximum === green) {
    hue = (blue - red) / delta + 2;
  } else {
    hue = (red - green) / delta + 4;
  }
  hue = (hue * 60 + 360) % 360;

  return `${round(hue)} ${round(saturation * 100)}% ${round(lightness * 100)}%`;
}

function round(value) {
  return Math.round(value * 10) / 10;
}

function variablesOf(theme) {
  const variables = {};
  for (const [name, token] of Object.entries(SHARED_VARIABLES)) {
    variables[name] = hexToHslTriplet(theme.tokens[token]);
  }
  for (const token of OWN_NAME_TOKENS) {
    variables[`--${token}`] = hexToHslTriplet(theme.tokens[token]);
  }

  return { 'color-scheme': theme.kind, ...variables };
}

function themeBlocks() {
  const blocks = {};
  for (const theme of themes) {
    const selector = theme.id === defaultDarkThemeId ? `:root, [data-theme="${theme.id}"]` : `[data-theme="${theme.id}"]`;
    blocks[selector] = variablesOf(theme);
  }

  return blocks;
}

function ownNameColors() {
  const colors = {};
  for (const token of OWN_NAME_TOKENS) {
    colors[token] = `hsl(var(--${token}) / <alpha-value>)`;
  }

  return colors;
}

const sansStack = ["'Archivo'", 'system-ui', 'sans-serif'];

const extend = {
  fontFamily: {
    display: sansStack,
    sans: sansStack,
    mono: sansStack,
  },
  colors: {
    rail: 'hsl(var(--rail) / <alpha-value>)',
    ...ownNameColors(),
  },
};

const themeBase = plugin(({ addBase, addComponents }) => {
  addBase({
    ':root': { '--radius': '0.75rem' },
    ...themeBlocks(),
    '*': { borderColor: 'hsl(var(--border))' },
    // The 8px scrollbar appears once a page grows past the screen (a list that finishes loading) and would push the
    // whole page sideways: Lighthouse measured that as a layout shift of 0.22. Keep its space reserved (BUG-065).
    html: { scrollbarGutter: 'stable' },
    body: {
      backgroundColor: 'hsl(var(--background))',
      color: 'hsl(var(--foreground))',
      fontFamily: "'Archivo', system-ui, sans-serif",
      fontWeight: '400',
      '-webkit-font-smoothing': 'antialiased',
      '-moz-osx-font-smoothing': 'grayscale',
    },
    '::selection': { background: 'hsl(var(--primary) / 0.35)' },
    '::-webkit-scrollbar': { width: '8px' },
    '::-webkit-scrollbar-track': { background: 'hsl(var(--background))' },
    '::-webkit-scrollbar-thumb': { background: 'hsl(var(--input))', borderRadius: '4px' },
    '::-webkit-scrollbar-thumb:hover': { background: 'hsl(var(--muted-foreground))' },
  });

  addComponents({
    /* Typography: sentence case, one family. */
    '.label': { '@apply text-[12.5px] font-medium text-muted-foreground': {} },
    // Kept while screens migrate; both now draw the plain label style. Removed with the last screen.
    '.eyebrow': { '@apply text-[12.5px] font-medium text-muted-foreground': {} },
    '.mono-label': { '@apply text-[12.5px] font-medium text-muted-foreground': {} },
    '.page-title': {
      '@apply font-bold leading-[1.04] tracking-[-0.03em]': {},
      fontSize: 'clamp(2.125rem, 4vw, 2.875rem)',
    },
    '.section-title': { '@apply text-lg font-semibold tracking-[-0.015em] sm:text-xl': {} },
    '.display-md': { '@apply font-semibold tracking-[-0.02em]': {} },

    /* Chips and status pills */
    '.chip': {
      '@apply inline-flex items-center gap-1.5 whitespace-nowrap rounded-md px-2 py-[5px] text-xs font-medium leading-none': {},
    },
    '.pill': {
      '@apply inline-flex items-center gap-[7px] rounded-full bg-primary/15 px-[11px] py-[6px] text-xs font-medium text-accent-text': {},
    },
    '.pill::before': {
      content: "''",
      '@apply h-1.5 w-1.5 rounded-full bg-current': {},
    },
    '.pill-muted': { '@apply bg-secondary text-muted-foreground': {} },

    /* Surfaces */
    '.panel': { '@apply rounded-[14px] border border-border bg-card': {} },

    /* Cover plates: flat colour and large initials, used when a game has no artwork */
    '.plate': {
      '@apply relative flex aspect-[2/3] w-full flex-col justify-between overflow-hidden p-4': {},
    },
    '.plate-initials': {
      '@apply font-extrabold leading-[0.85] tracking-[-0.05em] text-[44px] md:text-[56px]': {},
    },
    ...Object.fromEntries(
      [1, 2, 3, 4, 5, 6].map((number) => [
        `.plate-${number}`,
        { backgroundColor: `hsl(var(--plate-${number}))`, color: `hsl(var(--plate-${number}-ink))` },
      ]),
    ),
    '.cover-frame': {
      '@apply overflow-hidden rounded-xl border border-input transition duration-200': {},
    },
    '.card-link:hover .cover-frame, .card-link:focus-visible .cover-frame': {
      '@apply -translate-y-1 border-primary': {},
    },

    /* Prose overrides for the AI briefing */
    '.prose': {
      '--tw-prose-body': 'hsl(var(--secondary-foreground))',
      '--tw-prose-headings': 'hsl(var(--foreground))',
      '--tw-prose-bold': 'hsl(var(--foreground))',
      '--tw-prose-links': 'hsl(var(--accent-text))',
      '--tw-prose-counters': 'hsl(var(--muted-foreground))',
      '--tw-prose-bullets': 'hsl(var(--accent-text))',
      '--tw-prose-hr': 'hsl(var(--border))',
      '--tw-prose-quotes': 'hsl(var(--foreground))',
      '--tw-prose-quote-borders': 'hsl(var(--accent-text))',
    },
    '.prose h2, .prose h3': { letterSpacing: '-0.02em' },
  });
});

module.exports = { extend, plugin: themeBase, hexToHslTriplet };
