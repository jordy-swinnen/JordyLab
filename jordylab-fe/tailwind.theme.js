/**
 * Night Lab: the single source of the design tokens and component classes for every app
 * (jordylab, fna, gamecatalog). Each app's tailwind.config.js pulls in `extend` and `plugin`;
 * each app's styles.css only holds the @tailwind directives.
 *
 * Palette: blackberry-ink ground, Flare (vermilion) for action, Iris for Steam/secondary data.
 */
const plugin = require('tailwindcss/plugin');

const extend = {
  fontFamily: {
    display: ["'Bricolage Grotesque'", 'system-ui', 'sans-serif'],
    sans: ["'Hanken Grotesk'", 'system-ui', 'sans-serif'],
    mono: ["'Martian Mono'", 'ui-monospace', 'monospace'],
  },
  colors: {
    rail: 'hsl(var(--rail) / <alpha-value>)',
    iris: 'hsl(var(--iris) / <alpha-value>)',
    deep: 'hsl(var(--deep) / <alpha-value>)',
  },
};

const nightLab = plugin(({ addBase, addComponents }) => {
  addBase({
    ':root': {
      '--background': '255 25% 6%',
      '--foreground': '39 35% 92%',
      '--card': '255 24% 10%',
      '--card-foreground': '39 35% 92%',
      '--popover': '255 24% 10%',
      '--popover-foreground': '39 35% 92%',
      '--primary': '14 100% 62%',
      '--primary-foreground': '15 75% 6%',
      '--secondary': '255 24% 13%',
      '--secondary-foreground': '261 12% 67%',
      '--muted': '255 24% 13%',
      '--muted-foreground': '260 9% 58%',
      '--accent': '255 24% 13%',
      '--accent-foreground': '39 35% 92%',
      '--destructive': '0 72% 60%',
      '--destructive-foreground': '0 0% 98%',
      '--border': '258 19% 18%',
      '--input': '257 17% 25%',
      '--ring': '14 100% 62%',
      '--radius': '0.75rem',
      '--rail': '250 25% 5%',
      '--iris': '248 100% 77%',
      '--deep': '247 36% 23%',
    },
    '*': { borderColor: 'hsl(var(--border))' },
    body: {
      backgroundColor: 'hsl(var(--background))',
      color: 'hsl(var(--foreground))',
      fontFamily: "'Hanken Grotesk', system-ui, sans-serif",
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
    /* Typography */
    '.eyebrow': {
      '@apply font-mono text-[11px] font-medium uppercase tracking-[0.16em] text-primary': {},
    },
    '.mono-label': {
      '@apply font-mono text-[10.5px] font-medium uppercase tracking-[0.14em] text-muted-foreground': {},
    },
    '.page-title': {
      '@apply font-display font-bold leading-[0.95] tracking-[-0.035em]': {},
      fontSize: 'clamp(2.75rem, 5vw, 4.25rem)',
      fontVariationSettings: "'wdth' 86",
    },
    '.section-title': {
      '@apply font-display text-3xl font-bold tracking-[-0.025em]': {},
      fontVariationSettings: "'wdth' 88",
    },
    '.display-md': { '@apply font-display font-semibold tracking-[-0.02em]': {} },

    /* Status pills */
    '.pill': {
      '@apply inline-flex items-center gap-[7px] rounded-full bg-primary/15 px-[11px] py-[6px] font-mono text-[11px] font-medium uppercase tracking-[0.08em] text-primary': {},
    },
    '.pill::before': {
      content: "''",
      '@apply h-1.5 w-1.5 rounded-full bg-current': {},
    },
    '.pill-muted': { '@apply bg-secondary text-muted-foreground': {} },

    /* Surfaces */
    '.panel': { '@apply rounded-2xl border border-border bg-card': {} },

    /* Cover plates: flat colour + big initials, used when a game has no artwork */
    '.plate': {
      '@apply relative flex aspect-[2/3] w-full flex-col justify-between overflow-hidden p-4': {},
    },
    '.plate-initials': {
      '@apply font-display font-extrabold leading-[0.85] tracking-[-0.05em]': {},
      fontSize: 'clamp(2.5rem, 4.4vw, 3.6rem)',
      fontVariationSettings: "'wdth' 80",
    },
    '.cover-frame': {
      '@apply overflow-hidden rounded-2xl border border-input transition duration-200': {},
    },
    '.card-link:hover .cover-frame, .card-link:focus-visible .cover-frame': {
      '@apply -translate-y-1 border-primary': {},
    },

    /* Prose overrides for the AI briefing */
    '.prose': {
      '--tw-prose-body': 'hsl(39 20% 84%)',
      '--tw-prose-headings': 'hsl(var(--foreground))',
      '--tw-prose-bold': 'hsl(var(--foreground))',
      '--tw-prose-links': 'hsl(var(--primary))',
      '--tw-prose-counters': 'hsl(var(--muted-foreground))',
      '--tw-prose-bullets': 'hsl(var(--primary))',
      '--tw-prose-hr': 'hsl(var(--border))',
      '--tw-prose-quotes': 'hsl(var(--foreground))',
      '--tw-prose-quote-borders': 'hsl(var(--primary))',
    },
    '.prose h2, .prose h3': {
      fontFamily: "'Bricolage Grotesque', sans-serif",
      fontVariationSettings: "'wdth' 88",
      letterSpacing: '-0.025em',
    },
  });
});

module.exports = { extend, plugin: nightLab };
