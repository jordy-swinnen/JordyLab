import { Host } from '../gamecatalog.models';

export function aHostMock(overrides: Partial<Host> = {}): Host {
  return {
    id: '4c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
    hostname: 'jordybox',
    displayName: null,
    label: 'jordybox',
    ...overrides,
  };
}
