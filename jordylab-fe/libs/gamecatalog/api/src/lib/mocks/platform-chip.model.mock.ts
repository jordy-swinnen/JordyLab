import { PlatformChip } from '../gamecatalog.models';

export function aPlatformChipMock(overrides: Partial<PlatformChip> = {}): PlatformChip {
  return {
    name: 'SNES',
    family: 'NINTENDO',
    background: '#E60012',
    foreground: '#FFFFFF',
    border: null,
    ...overrides,
  };
}
