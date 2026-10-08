import { KnownConsole } from '../gamecatalog.models';

export function aKnownConsoleMock(overrides: Partial<KnownConsole> = {}): KnownConsole {
  return { name: 'Nintendo 64', family: 'NINTENDO', generation: 5, handheld: false, ...overrides };
}
