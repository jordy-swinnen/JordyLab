const { createGlobPatternsForDependencies } = require('@nx/angular/tailwind');
const { join } = require('path');

/** @type {import('tailwindcss').Config} */
module.exports = {
  presets: [require('@spartan-ng/ui-core/hlm-tailwind-preset')],
  content: [
    join(__dirname, 'src/**/!(*.stories|*.spec).{ts,html}'),
    ...createGlobPatternsForDependencies(__dirname),
    join(__dirname, '../../node_modules/@spartan-ng/**/!(*.spec).{js,mjs,ts}'),
  ],
  theme: {
    extend: {},
  },
  plugins: [require('@tailwindcss/typography')],
};
