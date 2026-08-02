import { PortfolioPosition } from '../fna.models';

export function aPortfolioPositionMock(overrides: Partial<PortfolioPosition> = {}): PortfolioPosition {
  return {
    id: 'p1',
    ticker: 'ABI',
    shareCount: 10,
    lastPrice: 70.5,
    lastPriceFetchedAt: '2026-08-01T06:00:00Z',
    ...overrides,
  };
}
