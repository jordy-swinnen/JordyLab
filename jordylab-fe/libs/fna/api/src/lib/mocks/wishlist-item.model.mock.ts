import { WishlistItem } from '../fna.models';

export function aWishlistItemMock(overrides: Partial<WishlistItem> = {}): WishlistItem {
  return {
    id: 'w1',
    ticker: 'ASML',
    lastPrice: 642.8,
    lastPriceFetchedAt: '2026-11-02T08:00:00Z',
    ...overrides,
  };
}
