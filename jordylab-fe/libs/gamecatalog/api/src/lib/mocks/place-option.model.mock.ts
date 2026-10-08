import { PlaceOption } from '../gamecatalog.models';

export function aPlaceOptionMock(overrides: Partial<PlaceOption> = {}): PlaceOption {
  return {
    id: '5b1c9a52-6d3e-4f70-8a24-9c0d1e2f3a4b',
    kind: 'HOST',
    label: 'Living room PC',
    ...overrides,
  };
}
