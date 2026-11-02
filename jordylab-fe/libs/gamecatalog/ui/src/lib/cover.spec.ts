import { coverInitials, coverPlateClass } from './cover';

describe('cover helpers', () => {
  it('derives up to three initials, skipping small words', () => {
    expect(coverInitials('Mega Man X')).toBe('MMX');
    expect(coverInitials('The Binding of Isaac: Rebirth')).toBe('BIR');
    expect(coverInitials('Portal 2')).toBe('P2');
  });

  it('uses two letters for single-word titles', () => {
    expect(coverInitials('CELESTE')).toBe('Ce');
  });

  it('returns the same plate for the same title', () => {
    expect(coverPlateClass('Celeste')).toBe(coverPlateClass('Celeste'));
  });

  it('only returns one of the six plates', () => {
    const titles = ['Celeste', 'Balatro', 'Aseprite', 'Hades', 'Chrono Trigger', 'Super Metroid', 'Earthbound', 'Portal 2'];

    expect(titles.map(coverPlateClass).every((plate) => /^plate-[1-6]$/.test(plate))).toBe(true);
  });
});
