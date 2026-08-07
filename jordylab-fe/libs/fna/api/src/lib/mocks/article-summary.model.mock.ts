import { ArticleSummary } from '../fna.models';

export function anArticleSummaryMock(overrides: Partial<ArticleSummary> = {}): ArticleSummary {
  return {
    id: '1',
    title: 'ECB holds rates steady',
    url: 'https://example.com/1',
    publishedAt: '2026-08-01T06:00:00Z',
    feedName: 'ECB Press Releases',
    ...overrides,
  };
}
