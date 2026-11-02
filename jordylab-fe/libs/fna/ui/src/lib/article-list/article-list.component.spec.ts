import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { anArticleSummaryMock, ArticleStore, ArticleSummary } from '@jordylab-fe/fna/api';
import { ArticleListComponent } from './article-list.component';

describe('ArticleListComponent', () => {
  const articles = signal<ArticleSummary[]>([]);
  const loading = signal(true);
  const error = signal<string | null>(null);

  const storeMock = {
    articles: articles.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    load: vi.fn(),
  };
  const daysAgo = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();

  const createComponent = createComponentFactory({
    component: ArticleListComponent,
    providers: [{ provide: ArticleStore, useValue: storeMock }],
  });

  let spectator: Spectator<ArticleListComponent>;

  beforeEach(() => {
    articles.set([]);
    loading.set(true);
    error.set(null);
    spectator = createComponent();
  });

  it('renders the article list view while loading', () => {
    expect(spectator.query('lib-article-list-view')).toBeTruthy();
  });

  it('displays a loading state before articles arrive', () => {
    expect(spectator.query('lib-article-list-view [role="status"]')).toHaveText('Fetching latest articles…');
  });

  it('displays populated articles', () => {
    loading.set(false);
    articles.set([anArticleSummaryMock({ title: 'ECB holds rates' })]);
    spectator.detectChanges();

    expect(spectator.query('h3')).toHaveText('ECB holds rates');
  });

  it('displays an empty state when no articles are available', () => {
    loading.set(false);
    articles.set([]);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('No articles yet.');
  });

  it('surfaces an error state when the store reports an error', () => {
    error.set('Failed to load articles.');
    spectator.detectChanges();

    expect(spectator.query('div.text-destructive')).toHaveText('Failed to load articles.');
  });

  it('groups articles under a Today heading with the calendar date', () => {
    loading.set(false);
    articles.set([anArticleSummaryMock({ publishedAt: new Date().toISOString() })]);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Today');
  });

  it('loads the articles again when the error state is retried', () => {
    storeMock.load.mockClear();
    error.set('Failed to load articles.');
    spectator.detectChanges();

    spectator.click('lib-error-state button');

    expect(storeMock.load).toHaveBeenCalledOnce();
  });

  it('labels yesterday and older days', () => {
    loading.set(false);
    articles.set([
      anArticleSummaryMock({ id: 'a1', publishedAt: daysAgo(1) }),
      anArticleSummaryMock({ id: 'a2', publishedAt: daysAgo(5) }),
    ]);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Yesterday');
  });

  it('gives each feed its own colour', () => {
    loading.set(false);
    articles.set([
      anArticleSummaryMock({ id: 'a1', feedName: 'ECB Press Releases' }),
      anArticleSummaryMock({ id: 'a2', feedName: 'Politico Europe' }),
      anArticleSummaryMock({ id: 'a3', feedName: 'Somewhere else' }),
    ]);
    spectator.detectChanges();

    expect(spectator.query('.text-accent-text')).toBeTruthy();
    expect(spectator.query('.text-series-2')).toBeTruthy();
  });
});
