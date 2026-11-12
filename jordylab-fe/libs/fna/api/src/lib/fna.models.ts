export interface ArticleSummary {
  id: string;
  title: string;
  url: string;
  publishedAt: string;
  feedName: string;
}

export interface PortfolioPosition {
  id: string;
  ticker: string;
  shareCount: number;
  lastPrice: number | null;
  lastPriceFetchedAt: string | null;
}

export interface PortfolioPositionRow extends PortfolioPosition {
  value: number | null;
}

/** A stock the investor is considering but does not hold: no share count, only a price (spec 016). */
export interface WishlistItem {
  id: string;
  ticker: string;
  lastPrice: number | null;
  lastPriceFetchedAt: string | null;
}

export interface Briefing {
  id: string;
  generatedAt: string;
  content: string;
  modelUsed: string;
}

/** The period choices of a holding's price history (spec 023, FR-003). */
export type PriceHistoryPeriod =
  | 'ONE_MONTH'
  | 'THREE_MONTHS'
  | 'ONE_YEAR'
  | 'MAX';

export type PriceHistoryStatus = 'AVAILABLE' | 'NO_HISTORY_YET';

/** One recorded day of a holding's history: the euro value of that day. */
export interface PriceHistoryPoint {
  date: string;
  priceInEuro: number;
}

/** Figures for the period. The change fields are null with a single point: a change of zero would mislead. */
export interface PriceHistorySummary {
  startDate: string;
  startPriceInEuro: number;
  endDate: string;
  endPriceInEuro: number;
  changeInEuro: number | null;
  changePercent: number | null;
  highestPriceInEuro: number;
  lowestPriceInEuro: number;
}

export interface PriceHistory {
  positionId: string;
  ticker: string;
  period: PriceHistoryPeriod;
  status: PriceHistoryStatus;
  windowStartDate: string | null;
  dataStartDate: string | null;
  points: PriceHistoryPoint[];
  summary: PriceHistorySummary | null;
  lastRecordedAt: string | null;
}
