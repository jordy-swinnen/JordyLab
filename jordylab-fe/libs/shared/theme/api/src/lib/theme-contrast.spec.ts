import { themes } from '../../../../../../themes.js';
import { hexToHslTriplet } from '../../../../../../tailwind.theme.js';

type Channels = readonly [number, number, number];

function channelsOfHex(hex: string): Channels {
  return [1, 3, 5].map((start) => parseInt(hex.slice(start, start + 2), 16)) as unknown as Channels;
}

function relativeLuminance(hex: string): number {
  const [red, green, blue] = channelsOfHex(hex).map((channel) => {
    const unit = channel / 255;
    return unit <= 0.03928 ? unit / 12.92 : ((unit + 0.055) / 1.055) ** 2.4;
  });

  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

function contrastRatio(foregroundHex: string, backgroundHex: string): number {
  const [lighter, darker] = [relativeLuminance(foregroundHex), relativeLuminance(backgroundHex)].sort((a, b) => b - a);

  return (lighter + 0.05) / (darker + 0.05);
}

function channelsOfTriplet(triplet: string): Channels {
  const [hueText, saturationText, lightnessText] = triplet.split(' ');
  const hue = parseFloat(hueText);
  const saturation = parseFloat(saturationText) / 100;
  const lightness = parseFloat(lightnessText) / 100;
  const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
  const channelAt = (offset: number) => {
    const position = (offset + hue / 30) % 12;
    return lightness - (chroma / 2) * Math.max(-1, Math.min(position - 3, 9 - position, 1));
  };

  return [channelAt(0), channelAt(8), channelAt(4)].map((unit) => Math.round(unit * 255)) as unknown as Channels;
}

const TEXT_ON_SURFACES: readonly (readonly [string, string])[] = ['text', 'text-2', 'text-3'].flatMap((text) =>
  ['ground', 'rail', 'surface'].map((surface) => [text, surface] as const),
);

const COMPONENT_PAIRS: readonly (readonly [string, string])[] = [
  ['accent-ink', 'accent'],
  ['accent-text', 'ground'],
  ['accent-text', 'surface'],
  ['installed', 'ground'],
  ['installed', 'surface'],
  ['mark-want', 'ground'],
  ['mark-want', 'surface'],
  ['mark-liked', 'ground'],
  ['mark-liked', 'surface'],
  ['mark-disliked', 'ground'],
  ['mark-disliked', 'surface'],
  ['platform-steam-ink', 'platform-steam'],
  ['platform-ps-ink', 'platform-ps'],
  ['platform-snes-ink', 'platform-snes'],
  ['rom-validated-ink', 'rom-validated'],
  ['text', 'line'],
  ['text-2', 'line'],
  ['ground', 'mark-liked'],
  ...[1, 2, 3, 4, 5, 6].map((number) => [`plate-${number}-ink`, `plate-${number}`] as const),
];

describe('themes', () => {
  it('offers three dark and three light themes', () => {
    expect(themes.filter((theme) => theme.kind === 'dark')).toHaveLength(3);
    expect(themes.filter((theme) => theme.kind === 'light')).toHaveLength(3);
  });

  for (const theme of themes) {
    describe(theme.name, () => {
      for (const [foreground, background] of [...TEXT_ON_SURFACES, ...COMPONENT_PAIRS]) {
        it(`shows ${foreground} on ${background} at 4.5:1 or better`, () => {
          const ratio = contrastRatio(theme.tokens[foreground], theme.tokens[background]);

          expect(ratio, `${foreground} ${theme.tokens[foreground]} on ${background} ${theme.tokens[background]}`).toBeGreaterThanOrEqual(4.5);
        });
      }

      it('converts every colour to HSL and back to within one step per channel', () => {
        for (const [token, hex] of Object.entries(theme.tokens)) {
          const restored = channelsOfTriplet(hexToHslTriplet(hex));
          const original = channelsOfHex(hex);
          const worstStep = Math.max(...original.map((channel, index) => Math.abs(channel - restored[index])));

          expect(worstStep, `${token} ${hex}`).toBeLessThanOrEqual(1);
        }
      });
    });
  }
});
