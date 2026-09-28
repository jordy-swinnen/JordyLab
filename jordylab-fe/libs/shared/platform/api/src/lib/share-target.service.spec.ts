import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { Router } from '@angular/router';
import { ShareTargetService } from './share-target.service';
import { PlatformService } from './platform.service';

const { addListener } = vi.hoisted(() => ({
  addListener: vi.fn(),
}));

vi.mock('@capgo/capacitor-share-target', () => ({
  CapacitorShareTarget: { addListener },
}));

describe('ShareTargetService', () => {
  let spectator: SpectatorService<ShareTargetService>;
  let httpMock: HttpTestingController;
  const navigateByUrl = vi.fn().mockResolvedValue(true);

  const createService = createServiceFactory({
    service: ShareTargetService,
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: Router, useValue: { navigateByUrl } },
    ],
  });

  function create(isNative: boolean) {
    spectator = createService({
      providers: [{ provide: PlatformService, useValue: { isNative: () => isNative } }],
    });
    httpMock = spectator.inject(HttpTestingController);
  }

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('does not register a listener on web', () => {
    create(false);

    spectator.service.listen();

    expect(addListener).not.toHaveBeenCalled();
  });

  it('stores the shared payload and navigates to the share landing route on a native share', () => {
    create(true);
    spectator.service.listen();
    const handler = addListener.mock.calls[0][1] as (event: {
      title: string;
      texts: string[];
      files: unknown[];
    }) => void;

    handler({
      title: 'A game site',
      texts: ['https://example.com/game'],
      files: [],
    });

    expect(spectator.service.pendingShare()).toEqual({
      title: 'A game site',
      texts: ['https://example.com/game'],
      files: [],
    });
    expect(navigateByUrl).toHaveBeenCalledWith('/mobile/share');
  });

  it('clears the pending share', () => {
    create(true);
    spectator.service.listen();
    const handler = addListener.mock.calls[0][1] as (event: {
      title: string;
      texts: string[];
      files: unknown[];
    }) => void;
    handler({ title: 't', texts: ['x'], files: [] });

    spectator.service.clear();

    expect(spectator.service.pendingShare()).toBeNull();
  });

  it('submits the shared URL to the manual FNA article endpoint', async () => {
    create(true);

    const submitPromise = spectator.service.submitToFna('https://example.com/article');
    httpMock
      .expectOne({ url: '/api/fna/articles/manual', method: 'POST' })
      .flush({ id: 'article-1' });
    await submitPromise;

    // Assertion is implicit in expectOne + flush not throwing — the request body is checked below.
  });

  it('sends the URL as the request body', () => {
    create(true);

    void spectator.service.submitToFna('https://example.com/article');

    const req = httpMock.expectOne({
      url: '/api/fna/articles/manual',
      method: 'POST',
    });
    expect(req.request.body).toEqual({ url: 'https://example.com/article' });
    req.flush({ id: 'article-1' });
  });
});
