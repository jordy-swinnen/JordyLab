import { PriceHistory } from '../fna.models';

export function aPriceHistoryMock(
  overrides: Partial<PriceHistory> = {},
): PriceHistory {
  return {
    positionId: 'p1',
    ticker: 'ABI',
    period: 'ONE_YEAR',
    status: 'AVAILABLE',
    windowStartDate: '2025-11-07',
    dataStartDate: '2025-11-07',
    points: [
      { date: '2025-11-07', priceInEuro: 1000 },
      { date: '2026-11-06', priceInEuro: 1250 },
    ],
    summary: {
      startDate: '2025-11-07',
      startPriceInEuro: 1000,
      endDate: '2026-11-06',
      endPriceInEuro: 1250,
      changeInEuro: 250,
      changePercent: 25,
      highestPriceInEuro: 1250,
      lowestPriceInEuro: 1000,
    },
    lastRecordedAt: '2026-11-06T16:00:00Z',
    ...overrides,
  };
}
