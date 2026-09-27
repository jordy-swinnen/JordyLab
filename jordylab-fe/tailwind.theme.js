/** Shared Tailwind theme extension for every app (Night Lab identity). */
module.exports = {
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
