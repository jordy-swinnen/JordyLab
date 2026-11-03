// Renders the JordyLab mark into the PNG app icons (web manifest and Apple touch icon). Run after changing the mark:
//   bun tools/render-icons.mjs
// The mark colours match the default theme's accent tile (bone) and ink; keep them in step with apps/jordylab/public/favicon.svg.
import { Resvg } from '@resvg/resvg-js';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const TILE = '#ECE8DD';
const INK = '#141414';
const OUTPUT_DIRECTORY = join(dirname(fileURLToPath(new URL(import.meta.url))), '..', 'apps', 'jordylab', 'public', 'icons');

// The mark's ink runs x 10.25–33.25 and y 6.75–39.25; it is centred on the tile, then scaled about that centre.
function markSvg(cornerRadius, markScale) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48" fill="none">
  <rect width="48" height="48" rx="${cornerRadius}" fill="${TILE}"/>
  <g transform="translate(24 24) scale(${markScale}) translate(-21.75 -23)">
    <path d="M30 10V27C30 32.5 26 36 21 36C17.5 36 15 34.5 13.5 32" stroke="${INK}" stroke-width="6.5" stroke-linecap="round" stroke-linejoin="round"/>
    <circle cx="17" cy="21" r="3.6" fill="${INK}"/>
    <circle cx="21.5" cy="12.5" r="2.4" fill="${INK}"/>
  </g>
</svg>`;
}

const ICONS = [
  // Launchers crop maskable icons to a centred safe zone, so the mark is scaled down to keep the whole J inside it.
  { file: 'icon-192.png', size: 192, cornerRadius: 13, markScale: 0.7 },
  { file: 'icon-512.png', size: 512, cornerRadius: 13, markScale: 0.7 },
  // iOS rounds the corners itself and shows the whole square, so the touch icon is a full square at full mark size.
  { file: 'apple-touch-icon.png', size: 180, cornerRadius: 0, markScale: 1 },
];

mkdirSync(OUTPUT_DIRECTORY, { recursive: true });
for (const icon of ICONS) {
  const png = new Resvg(markSvg(icon.cornerRadius, icon.markScale), { fitTo: { mode: 'width', value: icon.size } }).render().asPng();
  writeFileSync(join(OUTPUT_DIRECTORY, icon.file), png);
  console.log(`${icon.file}: ${icon.size}x${icon.size}, ${png.length} bytes`);
}
