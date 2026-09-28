import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ShareTargetService } from './share-target.service';
import { PlatformService } from './platform.service';

const { addListener } = vi.hoisted(() => ({
  addListener: vi.fn(),
}));

vi.mock('@capgo/capacitor-share-target', () => ({
  CapacitorShareTarget: { addListener },
}));

function createService(isNative: boolean) {
  const navigateByUrl = vi.fn().mockResolvedValue(true);

  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: PlatformService, useValue: { isNative: () => isNative } },
      { provide: Router, useValue: { navigateByUrl } },
    ],
  });

  return {
    service: TestBed.inject(ShareTargetService),
    httpMock: TestBed.inject(HttpTestingController),
    navigateByUrl,
  };
}

describe('ShareTargetService', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('does not register a listener on web', () => {
    const { service } = createService(false);

    service.listen();

    expect(addListener).not.toHaveBeenCalled();
  });

  it('stores the shared payload and navigates to the share landing route on a native share', () => {
    const { service, navigateByUrl } = createService(true);
    service.listen();
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

    expect(service.pendingShare()).toEqual({
      title: 'A game site',
      texts: ['https://example.com/game'],
      files: [],
    });
    expect(navigateByUrl).toHaveBeenCalledWith('/mobile/share');
  });

  it('clears the pending share', () => {
    const { service } = createService(true);
    service.listen();
    const handler = addListener.mock.calls[0][1] as (event: {
      title: string;
      texts: string[];
      files: unknown[];
    }) => void;
    handler({ title: 't', texts: ['x'], files: [] });

    service.clear();

    expect(service.pendingShare()).toBeNull();
  });

  it('submits the shared URL to the manual FNA article endpoint', async () => {
    const { service, httpMock } = createService(true);

    const submitPromise = service.submitToFna('https://example.com/article');
    httpMock
      .expectOne({ url: '/api/fna/articles/manual', method: 'POST' })
      .flush({ id: 'article-1' });
    await submitPromise;

    // Assertion is implicit in expectOne + flush not throwing — the request body is checked below.
  });

  it('sends the URL as the request body', () => {
    const { service, httpMock } = createService(true);

    void service.submitToFna('https://example.com/article');

    const req = httpMock.expectOne({
      url: '/api/fna/articles/manual',
      method: 'POST',
    });
    expect(req.request.body).toEqual({ url: 'https://example.com/article' });
    req.flush({ id: 'article-1' });
  });
});
