import { createComponentFactory } from '@ngneat/spectator/vitest';
import { MarkChipComponent } from './mark-chip.component';
import { RomChipComponent } from './rom-chip.component';
import { SourceLabelComponent } from './source-label.component';
import { StatusChipComponent } from './status-chip.component';

describe('StatusChipComponent', () => {
  const createComponent = createComponentFactory(StatusChipComponent);

  it('shows Installed with a check icon in teal, solid border', () => {
    const spectator = createComponent({ props: { status: 'INSTALLED' } });

    expect(spectator.element).toHaveText('Installed');
    expect(spectator.element.className).toContain('border-solid');
    expect(spectator.element.className).toContain('#2DD4BF');
    expect(spectator.query('svg path')).toBeTruthy();
  });

  it('shows Not installed with a dashed ring in amber, dashed border', () => {
    const spectator = createComponent({ props: { status: 'NOT_INSTALLED' } });

    expect(spectator.element).toHaveText('Not installed');
    expect(spectator.element.className).toContain('border-dashed');
    expect(spectator.element.className).toContain('#F5B84A');
    expect(spectator.query('svg')?.getAttribute('stroke-dasharray')).toBeTruthy();
  });

  it('uses two different colours and two different border styles for the two states', () => {
    const installed = createComponent({ props: { status: 'INSTALLED' } }).element.className;
    const missing = createComponent({ props: { status: 'NOT_INSTALLED' } }).element.className;

    expect(installed).not.toBe(missing);
  });
});

describe('SourceLabelComponent', () => {
  const createComponent = createComponentFactory(SourceLabelComponent);

  it.each([
    ['STEAM_OWNED', 'Steam (Owned)'],
    ['STEAM_FAMILY', 'Steam (Family)'],
    ['EMULATED', 'Emulated'],
    ['CONSOLE', 'Console'],
  ] as const)('names %s as %s', (source, label) => {
    const spectator = createComponent({ props: { source } });

    expect(spectator.element).toHaveText(label);
  });
});

describe('RomChipComponent', () => {
  const createComponent = createComponentFactory(RomChipComponent);

  it.each([
    ['VALIDATED', 'Validated', 'bg-[#9BE564]'],
    ['BROKEN', 'Broken ROM', 'bg-[#FF6B6B]'],
    ['UNKNOWN', 'Unknown', 'border-[#9A93AB]'],
    ['MIXED', 'Mixed', 'border-[#F5B84A]'],
  ] as const)('shows %s as "%s" with its own look and an icon', (state, label, look) => {
    const spectator = createComponent({ props: { state } });

    expect(spectator.element).toHaveText(label);
    expect(spectator.element.className).toContain(look);
    expect(spectator.query('svg')).toBeTruthy();
  });

  it('can say more, such as which machines validated it', () => {
    const spectator = createComponent({ props: { state: 'MIXED', detail: 'Validated on 1 of 2 hosts' } });

    expect(spectator.element).toHaveText('Validated on 1 of 2 hosts');
  });
});

describe('MarkChipComponent', () => {
  const createComponent = createComponentFactory(MarkChipComponent);

  it('shows the label and the public total', () => {
    const spectator = createComponent({ props: { mark: 'WANT_TO_PLAY', count: 5 } });

    expect(spectator.element).toHaveText('Want to play');
    expect(spectator.element).toHaveText('5');
    expect(spectator.element.getAttribute('aria-label')).toBe('Want to play: 5');
  });

  it('outlines your own vote and says so to a screen reader', () => {
    const spectator = createComponent({ props: { mark: 'PLAYED_LIKED', count: 2, mine: true } });

    expect(spectator.element.className).toContain('ring-');
    expect(spectator.element.getAttribute('aria-label')).toBe('Played & liked: 2 (your mark)');
  });

  it('does not outline a vote that is not yours', () => {
    const spectator = createComponent({ props: { mark: 'PLAYED_DISLIKED', count: 1 } });

    expect(spectator.element.className).not.toContain('ring-');
  });

  it('shows only the icon and count when compact but keeps the full text for screen readers', () => {
    const spectator = createComponent({ props: { mark: 'WANT_TO_PLAY', count: 5, compact: true } });

    expect(spectator.element).not.toHaveText('Want to play');
    expect(spectator.element.getAttribute('aria-label')).toBe('Want to play: 5');
  });

  it('gives the three marks three different tints', () => {
    const classes = (['WANT_TO_PLAY', 'PLAYED_LIKED', 'PLAYED_DISLIKED'] as const).map(
      (mark) => createComponent({ props: { mark } }).element.className,
    );

    expect(new Set(classes).size).toBe(3);
  });
});
