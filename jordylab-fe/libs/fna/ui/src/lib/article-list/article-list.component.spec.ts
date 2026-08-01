import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { Subject } from 'rxjs';
import { FnaApiService } from '@jordylab-fe/fna/api';
import { ArticleListComponent } from './article-list.component';

class FnaApiServiceMock {
  private articlesSubject = new Subject();
  getArticles = vi.fn(() => this.articlesSubject.asObservable());

  setArticles(articles: unknown[]) {
    this.articlesSubject.next(articles);
  }

  failArticles() {
    this.articlesSubject.error(new Error('network error'));
  }
}

describe('ArticleListComponent', () => {
  const createComponent = createComponentFactory({
    component: ArticleListComponent,
    providers: [{ provide: FnaApiService, useClass: FnaApiServiceMock }],
  });

  it('renders the article list view while loading', () => {
    const spectator: Spectator<ArticleListComponent> = createComponent();

    expect(spectator.query('lib-article-list-view')).toBeTruthy();
  });

  it('displays populated articles', () => {
    const spectator: Spectator<ArticleListComponent> = createComponent();
    const service = spectator.inject(FnaApiService) as unknown as FnaApiServiceMock;
    service.setArticles([
      {
        id: '1',
        title: 'ECB holds rates',
        url: 'https://example.com/1',
        publishedAt: '2026-08-01T06:00:00Z',
        feedName: 'ECB Press Releases',
      },
    ]);
    spectator.detectChanges();

    expect(spectator.query('h3')).toHaveText('ECB holds rates');
  });

  it('displays a loading state before articles arrive', () => {
    const spectator: Spectator<ArticleListComponent> = createComponent();

    expect(spectator.query('lib-article-list-view span.text-sm')).toHaveText('Fetching latest articles…');
  });

  it('displays an empty state when no articles are available', () => {
    const spectator: Spectator<ArticleListComponent> = createComponent();
    const service = spectator.inject(FnaApiService) as unknown as FnaApiServiceMock;
    service.setArticles([]);
    spectator.detectChanges();

    expect(spectator.query('p.text-sm')).toHaveText('No articles available.');
  });

  it('surfaces an error state when the API call fails', () => {
    const spectator: Spectator<ArticleListComponent> = createComponent();
    const service = spectator.inject(FnaApiService) as unknown as FnaApiServiceMock;
    service.failArticles();
    spectator.detectChanges();

    expect(spectator.query('div.text-destructive')).toHaveText('Failed to load articles.');
  });
});
