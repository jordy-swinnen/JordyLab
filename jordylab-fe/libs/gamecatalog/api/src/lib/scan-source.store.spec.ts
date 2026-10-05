import { HttpErrorResponse } from '@angular/common/http';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { LibraryStatus, RefreshAll, ScanSource } from './gamecatalog.models';
import { ScanSourceStore } from './scan-source.store';
import { aRefreshAllMock } from './mocks/refresh-all.model.mock';
import { aRefreshCountMock } from './mocks/refresh-count.model.mock';
import { aScanSourceMock } from './mocks/scan-source.model.mock';

describe('ScanSourceStore', () => {
  let spectator: SpectatorService<ScanSourceStore>;
  const getSources = vi.fn<GameCatalogApiService['getSources']>();
  const setSourceEnabled = vi.fn<GameCatalogApiService['setSourceEnabled']>();
  const getScanClient = vi.fn<GameCatalogApiService['getScanClient']>();
  const refreshPending = vi.fn<GameCatalogApiService['refreshPending']>();
  const getLibraryStatus = vi.fn<GameCatalogApiService['getLibraryStatus']>();
  const syncOwnedLibrary = vi.fn<GameCatalogApiService['syncOwnedLibrary']>();
  const syncFamilyLibrary = vi.fn<GameCatalogApiService['syncFamilyLibrary']>();

  const createService = createServiceFactory({
    service: ScanSourceStore,
    providers: [
      {
        provide: GameCatalogApiService,
        useValue: {
          getSources,
          setSourceEnabled,
          getScanClient,
          refreshPending,
          getLibraryStatus,
          syncOwnedLibrary,
          syncFamilyLibrary,
        },
      },
    ],
  });

  beforeEach(() => {
    getSources.mockReset();
    getSources.mockReturnValue(of([aScanSourceMock()]));
    setSourceEnabled.mockReset();
    setSourceEnabled.mockImplementation((id, enabled) => of({ id, enabled }));
    getScanClient.mockReset();
    getScanClient.mockReturnValue(of(new Blob(['#!/bin/sh'])));
    refreshPending.mockReset();
    refreshPending.mockReturnValue(
      of(aRefreshAllMock({ metadata: aRefreshCountMock({ processed: 0 }), enrichment: aRefreshCountMock({ processed: 0 }) }))
    );
    getLibraryStatus.mockReset();
    getLibraryStatus.mockReturnValue(of(anEmptyLibraryStatus()));
    syncOwnedLibrary.mockReset();
    syncFamilyLibrary.mockReset();
  });

  describe('load', () => {
    it('loads the sources on construction', () => {
      spectator = createService();

      expect(spectator.service.sources()).toEqual([aScanSourceMock()]);
      expect(spectator.service.loading()).toBe(false);
      expect(spectator.service.error()).toBeNull();
    });

    it('is loading until the sources arrive', () => {
      const sources = new Subject<ScanSource[]>();
      getSources.mockReturnValue(sources.asObservable());
      spectator = createService();

      expect(spectator.service.loading()).toBe(true);

      sources.next([]);

      expect(spectator.service.loading()).toBe(false);
    });

    it('sets an error message when loading fails', () => {
      getSources.mockReturnValue(throwError(() => new Error('network error')));
      spectator = createService();

      expect(spectator.service.error()).toBe('Failed to load scan sources.');
      expect(spectator.service.loading()).toBe(false);
      expect(spectator.service.sources()).toEqual([]);
    });

    it('reloads the sources and clears a previous error', () => {
      getSources.mockReturnValueOnce(throwError(() => new Error('network error')));
      spectator = createService();

      spectator.service.load();

      expect(spectator.service.error()).toBeNull();
      expect(spectator.service.sources()).toEqual([aScanSourceMock()]);
    });
  });

  describe('toggle', () => {
    beforeEach(() => {
      spectator = createService();
    });

    it('disables an enabled source', () => {
      spectator.service.toggle(aScanSourceMock());

      expect(setSourceEnabled).toHaveBeenCalledWith(aScanSourceMock().id, false);
      expect(spectator.service.sources()[0].enabled).toBe(false);
      expect(spectator.service.togglingId()).toBeNull();
    });

    it('enables a disabled source', () => {
      spectator.service.toggle(aScanSourceMock({ enabled: false }));

      expect(setSourceEnabled).toHaveBeenCalledWith(aScanSourceMock().id, true);
      expect(spectator.service.sources()[0].enabled).toBe(true);
    });

    it('only updates the toggled source', () => {
      getSources.mockReturnValue(of([aScanSourceMock(), aScanSourceMock({ id: 'other', sourceKey: 'other:STEAM' })]));
      spectator.service.load();

      spectator.service.toggle(aScanSourceMock());

      expect(spectator.service.sources().map((source) => source.enabled)).toEqual([false, true]);
    });

    it('marks the source as toggling until the response arrives', () => {
      const response = new Subject<{ id: string; enabled: boolean }>();
      setSourceEnabled.mockReturnValue(response.asObservable());

      spectator.service.toggle(aScanSourceMock());

      expect(spectator.service.togglingId()).toBe(aScanSourceMock().id);

      response.next({ id: aScanSourceMock().id, enabled: false });

      expect(spectator.service.togglingId()).toBeNull();
    });

    it('ignores a toggle while another is in flight', () => {
      setSourceEnabled.mockReturnValue(new Subject<{ id: string; enabled: boolean }>().asObservable());

      spectator.service.toggle(aScanSourceMock());
      spectator.service.toggle(aScanSourceMock({ id: 'other' }));

      expect(setSourceEnabled).toHaveBeenCalledTimes(1);
    });

    it('reports a failure and leaves the source unchanged', () => {
      setSourceEnabled.mockReturnValue(throwError(() => new Error('network error')));

      spectator.service.toggle(aScanSourceMock());

      expect(spectator.service.error()).toBe("Failed to update 'jordybox:STEAM'.");
      expect(spectator.service.togglingId()).toBeNull();
      expect(spectator.service.sources()[0].enabled).toBe(true);
    });
  });

  describe('downloadClient', () => {
    const onReady = vi.fn<(blob: Blob) => void>();

    beforeEach(() => {
      onReady.mockReset();
      spectator = createService();
    });

    it('hands the generated script to the callback', () => {
      const blob = new Blob(['#!/bin/sh']);
      getScanClient.mockReturnValue(of(blob));

      spectator.service.downloadClient('steam', onReady);

      expect(getScanClient).toHaveBeenCalledWith('steam');
      expect(onReady).toHaveBeenCalledWith(blob);
      expect(spectator.service.downloading()).toBeNull();
    });

    it('marks the library as downloading until the script arrives', () => {
      const script = new Subject<Blob>();
      getScanClient.mockReturnValue(script.asObservable());

      spectator.service.downloadClient('emudeck', onReady);

      expect(spectator.service.downloading()).toBe('emudeck');

      script.next(new Blob([]));

      expect(spectator.service.downloading()).toBeNull();
    });

    it('ignores a download while another is in flight', () => {
      getScanClient.mockReturnValue(new Subject<Blob>().asObservable());

      spectator.service.downloadClient('steam', onReady);
      spectator.service.downloadClient('emudeck', onReady);

      expect(getScanClient).toHaveBeenCalledTimes(1);
    });

    it('reports a failure without calling the callback', () => {
      getScanClient.mockReturnValue(throwError(() => new Error('network error')));

      spectator.service.downloadClient('steam', onReady);

      expect(spectator.service.error()).toBe('Failed to generate the steam scan client.');
      expect(spectator.service.downloading()).toBeNull();
      expect(onReady).not.toHaveBeenCalled();
    });
  });

  describe('refreshPending', () => {
    it('drains in batches until nothing remains', () => {
      spectator = createService();
      refreshPending
        .mockReturnValueOnce(
          of(
            aRefreshAllMock({
              metadata: aRefreshCountMock({ processed: 5, remaining: 5 }),
              enrichment: aRefreshCountMock({ processed: 2, remaining: 5 }),
            })
          )
        )
        .mockReturnValueOnce(
          of(
            aRefreshAllMock({
              metadata: aRefreshCountMock({ processed: 5 }),
              enrichment: aRefreshCountMock({ processed: 5 }),
            })
          )
        );

      spectator.service.refreshPending();

      expect(refreshPending).toHaveBeenCalledTimes(2);
      expect(spectator.service.refreshingPending()).toBe(false);
      expect(spectator.service.refreshProgress()).toBeNull();
      expect(spectator.service.error()).toBeNull();
    });

    it('stops with an error when a batch makes no progress', () => {
      spectator = createService();
      refreshPending.mockReturnValue(
        of(
          aRefreshAllMock({
            metadata: aRefreshCountMock({ processed: 0, remaining: 3 }),
            enrichment: aRefreshCountMock({ processed: 0, remaining: 2 }),
          })
        )
      );

      spectator.service.refreshPending();

      expect(refreshPending).toHaveBeenCalledTimes(2);
      expect(spectator.service.refreshingPending()).toBe(false);
      expect(spectator.service.error()).toContain('stalled');
    });

    it('reports a failure and stops refreshing', () => {
      spectator = createService();
      refreshPending.mockReturnValue(throwError(() => new Error('network error')));

      spectator.service.refreshPending();

      expect(spectator.service.error()).toBe('Failed to refresh catalog data.');
      expect(spectator.service.refreshingPending()).toBe(false);
      expect(spectator.service.refreshProgress()).toBeNull();
    });

    it('ignores a refresh while one is already running', () => {
      spectator = createService();
      const inFlight = new Subject<RefreshAll>();
      refreshPending.mockReturnValue(inFlight.asObservable());

      spectator.service.refreshPending();
      spectator.service.refreshPending();

      expect(refreshPending).toHaveBeenCalledTimes(1);
    });
  });

  describe('library sync failures', () => {
    beforeEach(() => {
      spectator = createService();
    });

    const rejected = (body: unknown, status = 502) =>
      throwError(() => new HttpErrorResponse({ status, error: body }));

    it('tells the user a family token was refused and how to get a new one', () => {
      syncFamilyLibrary.mockReturnValue(rejected({ reason: 'FAMILY_SYNC_FAILED', errorCode: 'TOKEN_EXPIRED' }));

      spectator.service.syncFamilyLibrary('token');

      expect(spectator.service.error()).toContain('Steam rejected the token');
      expect(spectator.service.error()).toContain('signed in to store.steampowered.com');
      expect(spectator.service.librarySyncing()).toBeNull();
    });

    it('says when the account has no Steam Family group', () => {
      syncFamilyLibrary.mockReturnValue(rejected({ reason: 'FAMILY_SYNC_FAILED', errorCode: 'NO_FAMILY_GROUP' }));

      spectator.service.syncFamilyLibrary('token');

      expect(spectator.service.error()).toContain('not in a Steam Family group');
    });

    it('says when the owned library cannot sync because Steam is not configured', () => {
      syncOwnedLibrary.mockReturnValue(rejected({ reason: 'STEAM_NOT_CONFIGURED', errorCode: null }, 409));

      spectator.service.syncOwnedLibrary();

      expect(spectator.service.error()).toContain('not configured on the server');
    });

    it('falls back to a plain message when the failure has no reason', () => {
      syncFamilyLibrary.mockReturnValue(throwError(() => new Error('offline')));

      spectator.service.syncFamilyLibrary('token');

      expect(spectator.service.error()).toBe('Failed to sync the family library.');
    });
  });
});


function anEmptyLibraryStatus(): LibraryStatus {
  const empty = {
    lastSuccessAt: null,
    lastOutcome: null,
    entriesActive: 0,
    metadataCalls: 0,
    aiCalls: 0,
    familyTokenPresent: false,
    stale: false,
  } as const;

  return { owned: { ...empty }, family: { ...empty }, ownedConfigured: false };
}
