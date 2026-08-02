import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { anArticleSummaryMock } from './mocks/article-summary.model.mock';
import { ArticleStore } from './article.store';

describe('ArticleStore', () => {
  let spectator: SpectatorService<ArticleStore>;
  let httpMock: HttpTestingController;

  const createService = createServiceFactory({
    service: ArticleStore,
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });

  beforeEach(() => {
    spectator = createService();
    httpMock = spectator.inject(HttpTestingController);
  });

  it('loads articles on construction', () => {
    const req = httpMock.expectOne('/api/fna/articles');
    req.flush([anArticleSummaryMock()]);

    expect(spectator.service.articles()).toHaveLength(1);
    expect(spectator.service.loading()).toBe(false);
    expect(spectator.service.hasArticles()).toBe(true);
  });

  it('sets an error message when the request fails', () => {
    const req = httpMock.expectOne('/api/fna/articles');
    req.error(new ProgressEvent('error'));

    expect(spectator.service.error()).toBe('Failed to load articles.');
    expect(spectator.service.loading()).toBe(false);
  });

  it('reloads articles on load()', () => {
    httpMock.expectOne('/api/fna/articles').flush([]);

    spectator.service.load();

    const req = httpMock.expectOne('/api/fna/articles');
    req.flush([anArticleSummaryMock()]);

    expect(spectator.service.articles()).toHaveLength(1);
  });
});
