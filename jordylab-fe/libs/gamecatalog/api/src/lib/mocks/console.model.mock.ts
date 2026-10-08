import { GameConsole } from '../gamecatalog.models';
import { aPlatformChipMock } from './platform-chip.model.mock';

export function aConsoleMock(overrides: Partial<GameConsole> = {}): GameConsole {
  return {
    id: '5c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
    platform: 'Nintendo Switch',
    family: 'NINTENDO',
    chip: aPlatformChipMock({ name: 'Nintendo Switch' }),
    name: 'Switch dock',
    label: 'Switch dock',
    gameCount: 3,
    ...overrides,
  };
}
