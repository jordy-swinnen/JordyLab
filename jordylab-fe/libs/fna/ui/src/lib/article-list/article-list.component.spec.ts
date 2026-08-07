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
    expect(spectator.query('lib-article-list-view span.text-sm')).toHaveText('Fetching latest articles…');
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

    expect(spectator.query('p.text-sm')).toHaveText('No articles available.');
  });

  it('surfaces an error state when the store reports an error', () => {
    error.set('Failed to load articles.');
    spectator.detectChanges();

    expect(spectator.query('div.text-destructive')).toHaveText('Failed to load articles.');
  });
});
