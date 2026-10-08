import { createComponentFactory } from '@ngneat/spectator/vitest';
import { MarkType, RomSummaryState } from '@jordylab-fe/gamecatalog/api';
import { MarkChipComponent } from './mark-chip.component';
import { RomChipComponent } from './rom-chip.component';
import { StatusChipComponent } from './status-chip.component';

type Rgb = [number, number, number];

/** The card colour of the Night Lab theme (`--card: 255 24% 10%`), which the status, ROM and mark chips sit on. */
const CARD = hslToRgb(255, 24, 10);
const MINIMUM_TEXT_CONTRAST = 4.5;

function hslToRgb(hue: number, saturation: number, lightness: number): Rgb {
  const s = saturation / 100;
  const l = lightness / 100;
  const k = (n: number) => (n + hue / 30) % 12;
  const a = s * Math.min(l, 1 - l);
  const channel = (n: number) => l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));

  return [channel(0) * 255, channel(8) * 255, channel(4) * 255];
}

function hexToRgb(hex: string): Rgb {
  return [1, 3, 5].map((start) => parseInt(hex.slice(start, start + 2), 16)) as Rgb;
}

function blend(foreground: Rgb, background: Rgb, opacity: number): Rgb {
  return foreground.map((value, index) => value * opacity + background[index] * (1 - opacity)) as Rgb;
}

function luminance(rgb: Rgb): number {
  const [red, green, blue] = rgb.map((value) => {
    const unit = value / 255;

    return unit <= 0.03928 ? unit / 12.92 : ((unit + 0.055) / 1.055) ** 2.4;
  });

  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

function contrast(first: Rgb, second: Rgb): number {
  const [lighter, darker] = [luminance(first), luminance(second)].sort((a, b) => b - a);

  return (lighter + 0.05) / (darker + 0.05);
}

/** Reads the colours straight out of the classes the chip really renders, so the test cannot drift from the component. */
function colours(className: string): { text: Rgb; background: Rgb } {
  const text = /(?:^|\s)text-\[(#[0-9A-Fa-f]{6})\]/.exec(className);
  if (!text) {
    throw new Error(`no text colour in "${className}"`);
  }
  const solid = /(?:^|\s)bg-\[(#[0-9A-Fa-f]{6})\](?:\/(\d+))?/.exec(className);
  const background = solid
    ? blend(hexToRgb(solid[1]), CARD, solid[2] ? Number(solid[2]) / 100 : 1)
    : CARD;

  return { text: hexToRgb(text[1]), background };
}

describe('chip contrast (WCAG AA, 4.5:1 for text)', () => {
  const statusChip = createComponentFactory(StatusChipComponent);
  const romChip = createComponentFactory(RomChipComponent);
  const markChip = createComponentFactory(MarkChipComponent);

  it.each(['INSTALLED', 'NOT_INSTALLED'] as const)('the %s chip text reads on the card', (status) => {
    const { text, background } = colours(statusChip({ props: { status } }).element.className);

    expect(contrast(text, background)).toBeGreaterThanOrEqual(MINIMUM_TEXT_CONTRAST);
  });

  it.each(['VALIDATED', 'BROKEN', 'UNKNOWN', 'MIXED'] as RomSummaryState[])('the %s ROM chip text reads on its fill', (state) => {
    const className = romChip({ props: { state } }).element.className;
    const solid = /bg-\[#/.test(className);
    const classNameWithText = solid ? className : `${className}`;
    const outline = /border-\[(#[0-9A-Fa-f]{6})\]/.exec(className);
    const text = outline ? { text: hexToRgb(outline[1]), background: CARD } : colours(classNameWithText);

    expect(contrast(text.text, text.background)).toBeGreaterThanOrEqual(MINIMUM_TEXT_CONTRAST);
  });

  it.each(['WANT_TO_PLAY', 'PLAYED_LIKED', 'PLAYED_DISLIKED'] as MarkType[])('the %s mark chip text reads on its tint', (mark) => {
    const { text, background } = colours(markChip({ props: { mark, count: 1 } }).element.className);

    expect(contrast(text, background)).toBeGreaterThanOrEqual(MINIMUM_TEXT_CONTRAST);
  });

  it('computes the ratio the way WCAG does (white on black is 21)', () => {
    expect(contrast([255, 255, 255], [0, 0, 0])).toBeCloseTo(21, 5);
  });
});
