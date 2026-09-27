import { coverInitials, coverPalette, platformTagClass } from './cover';

describe('cover helpers', () => {
  it('derives up to three initials, skipping small words', () => {
    expect(coverInitials('Mega Man X')).toBe('MMX');
    expect(coverInitials('The Binding of Isaac: Rebirth')).toBe('BIR');
    expect(coverInitials('Portal 2')).toBe('P2');
  });

  it('uses two letters for single-word titles', () => {
    expect(coverInitials('CELESTE')).toBe('Ce');
  });

  it('returns the same palette for the same title', () => {
    expect(coverPalette('Celeste')).toEqual(coverPalette('Celeste'));
  });

  it('tints Steam with iris and everything else with flare', () => {
    expect(platformTagClass('Steam')).toContain('text-iris');
    expect(platformTagClass('steam')).toContain('text-iris');
    expect(platformTagClass('SNES')).toContain('text-primary');
  });
});
