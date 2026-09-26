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

export interface Briefing {
  id: string;
  generatedAt: string;
  content: string;
  modelUsed: string;
}
