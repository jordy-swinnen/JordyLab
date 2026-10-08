import { HttpErrorResponse } from '@angular/common/http';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { HealthExceptions, LibraryStatus, SourcesOverview } from './gamecatalog.models';
import { ScanSourceStore } from './scan-source.store';
import { aHostMock } from './mocks/host.model.mock';
import { aScanSourceMock } from './mocks/scan-source.model.mock';
import { aSourcesOverviewMock } from './mocks/sources-overview.model.mock';
import { aLibraryHealthMock } from './mocks/library-health.model.mock';

describe('ScanSourceStore', () => {
  let spectator: SpectatorService<ScanSourceStore>;
  const getSources = vi.fn<GameCatalogApiService['getSources']>();
  const getHealthExceptions = vi.fn<GameCatalogApiService['getHealthExceptions']>();
  const setSourceEnabled = vi.fn<GameCatalogApiService['setSourceEnabled']>();
  const getHideImpact = vi.fn<GameCatalogApiService['getHideImpact']>();
  const setHostDisplayName = vi.fn<GameCatalogApiService['setHostDisplayName']>();
  const getScanClient = vi.fn<GameCatalogApiService['getScanClient']>();
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
          getHealthExceptions,
          setSourceEnabled,
          getHideImpact,
          setHostDisplayName,
          getScanClient,
          getLibraryStatus,
          syncOwnedLibrary,
          syncFamilyLibrary,
        },
      },
    ],
  });

  beforeEach(() => {
    getSources.mockReset();
    getSources.mockReturnValue(of(aSourcesOverviewMock()));
    setHostDisplayName.mockReset();
    getHideImpact.mockReset();
    getHideImpact.mockReturnValue(of({ hiddenGames: 87, stillVisibleElsewhere: 5 }));
    setSourceEnabled.mockReset();
    setSourceEnabled.mockImplementation((id, enabled) => of({ id, enabled }));
    getScanClient.mockReset();
    getScanClient.mockReturnValue(of(new Blob(['#!/bin/sh'])));
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

    it('exposes the library health that comes with the sources', () => {
      spectator = createService();

      expect(spectator.service.health()).toEqual(aLibraryHealthMock());
    });

    it('is loading until the sources arrive', () => {
      const sources = new Subject<SourcesOverview>();
      getSources.mockReturnValue(sources.asObservable());
      spectator = createService();

      expect(spectator.service.loading()).toBe(true);

      sources.next(aSourcesOverviewMock({ sources: [] }));

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
      getSources.mockReturnValue(of(aSourcesOverviewMock({ sources: [aScanSourceMock(), aScanSourceMock({ id: 'other', sourceKey: 'other:STEAM' })] })));
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

  describe('turning a source off', () => {
    beforeEach(() => {
      spectator = createService();
    });

    it('asks what would be hidden before disabling and changes nothing yet', () => {
      const source = aScanSourceMock();

      spectator.service.requestToggle(source);

      expect(getHideImpact).toHaveBeenCalledWith(source.id);
      expect(spectator.service.disableQuote()).toEqual({ source, impact: { hiddenGames: 87, stillVisibleElsewhere: 5 } });
      expect(setSourceEnabled).not.toHaveBeenCalled();
    });

    it('disables the source once the admin confirms', () => {
      const source = aScanSourceMock();
      spectator.service.requestToggle(source);

      spectator.service.confirmDisable();

      expect(setSourceEnabled).toHaveBeenCalledWith(source.id, false);
      expect(spectator.service.disableQuote()).toBeNull();
    });

    it('leaves the source on when the admin cancels', () => {
      spectator.service.requestToggle(aScanSourceMock());

      spectator.service.cancelDisable();

      expect(spectator.service.disableQuote()).toBeNull();
      expect(setSourceEnabled).not.toHaveBeenCalled();
    });

    it('turns a disabled source back on at once, with no question', () => {
      const off = aScanSourceMock({ enabled: false });

      spectator.service.requestToggle(off);

      expect(getHideImpact).not.toHaveBeenCalled();
      expect(setSourceEnabled).toHaveBeenCalledWith(off.id, true);
    });

    it('still asks, without a number, when the impact cannot be fetched', () => {
      getHideImpact.mockReturnValue(throwError(() => new Error('offline')));

      spectator.service.requestToggle(aScanSourceMock());

      expect(spectator.service.disableQuote()?.impact.hiddenGames).toBe(-1);
    });
  });

  describe('naming a host', () => {
    const livingRoom = aHostMock({ displayName: 'Living room PC', label: 'Living room PC' });
    const steam = aScanSourceMock();
    const emulation = aScanSourceMock({ id: 'e1', sourceKey: 'jordybox:EMUDECK', sourceType: 'EMUDECK' });
    const otherHost = aScanSourceMock({ id: 'o1', hostId: 'other-host', hostname: 'macbook', label: 'macbook' });

    beforeEach(() => {
      getSources.mockReturnValue(of(aSourcesOverviewMock({ sources: [steam, emulation, otherHost] })));
      setHostDisplayName.mockReturnValue(of(livingRoom));
      spectator = createService();
    });

    it('opens one editor at a time and closes it again', () => {
      spectator.service.startEditingHostName(steam.id);
      expect(spectator.service.editingSourceId()).toBe(steam.id);

      spectator.service.startEditingHostName(emulation.id);
      expect(spectator.service.editingSourceId()).toBe(emulation.id);

      spectator.service.stopEditingHostName();
      expect(spectator.service.editingSourceId()).toBeNull();
    });

    it('applies the new name to every source of the host and nowhere else', () => {
      spectator.service.startEditingHostName(steam.id);

      spectator.service.renameHost(steam.hostId, ' Living room PC ');

      expect(setHostDisplayName).toHaveBeenCalledWith(steam.hostId, 'Living room PC');
      expect(spectator.service.sources().map((source) => [source.id, source.label])).toEqual([
        [steam.id, 'Living room PC'],
        ['e1', 'Living room PC'],
        ['o1', 'macbook'],
      ]);
      expect(spectator.service.sources()[0].displayName).toBe('Living room PC');
      expect(spectator.service.editingSourceId()).toBeNull();
      expect(spectator.service.renamingHostId()).toBeNull();
    });

    it('sends null for a blank name so the hostname comes back', () => {
      setHostDisplayName.mockReturnValue(of(aHostMock()));

      spectator.service.renameHost(steam.hostId, '   ');

      expect(setHostDisplayName).toHaveBeenCalledWith(steam.hostId, null);
      expect(spectator.service.sources()[0].label).toBe('jordybox');
    });

    it('says the name is taken and keeps the editor open', () => {
      spectator.service.startEditingHostName(steam.id);
      setHostDisplayName.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 409, error: { reason: 'NAME_TAKEN' } })),
      );

      spectator.service.renameHost(steam.hostId, 'Nintendo Switch');

      expect(spectator.service.hostNameProblem()).toBe('That name is already used by another host or console.');
      expect(spectator.service.editingSourceId()).toBe(steam.id);
      expect(spectator.service.sources()[0].label).toBe('jordybox');
      expect(spectator.service.renamingHostId()).toBeNull();
    });

    it('says the name is too long', () => {
      setHostDisplayName.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 400, error: { reason: 'NAME_TOO_LONG' } })),
      );

      spectator.service.renameHost(steam.hostId, 'x'.repeat(41));

      expect(spectator.service.hostNameProblem()).toBe('A name can be at most 40 characters.');
    });

    it('falls back to a general message for any other failure and forgets it when the editor closes', () => {
      setHostDisplayName.mockReturnValue(throwError(() => new Error('offline')));

      spectator.service.renameHost(steam.hostId, 'Anything');
      expect(spectator.service.hostNameProblem()).toBe('Could not save the name. Try again.');

      spectator.service.stopEditingHostName();
      expect(spectator.service.hostNameProblem()).toBeNull();
    });

    it('ignores a second save while one is in flight', () => {
      const pending = new Subject<ReturnType<typeof aHostMock>>();
      setHostDisplayName.mockReturnValue(pending.asObservable());

      spectator.service.renameHost(steam.hostId, 'One');
      spectator.service.renameHost(steam.hostId, 'Two');

      expect(setHostDisplayName).toHaveBeenCalledTimes(1);
      expect(spectator.service.renamingHostId()).toBe(steam.hostId);
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

  describe('health exceptions', () => {
    beforeEach(() => {
      spectator = createService();
    });

    it('loads the games behind one count and can hide them again', () => {
      const exceptions: HealthExceptions = { kind: 'COVER', total: 12, games: [{ id: 'g-1', title: 'Obscure Game' }] };
      getHealthExceptions.mockReturnValue(of(exceptions));

      spectator.service.showHealthExceptions('COVER');

      expect(getHealthExceptions).toHaveBeenCalledWith('COVER');
      expect(spectator.service.healthExceptions()).toEqual(exceptions);
      expect(spectator.service.loadingExceptions()).toBeNull();

      spectator.service.hideHealthExceptions();
      expect(spectator.service.healthExceptions()).toBeNull();
    });

    it('shows nothing when the list cannot be loaded', () => {
      getHealthExceptions.mockReturnValue(throwError(() => new Error('down')));

      spectator.service.showHealthExceptions('INDEX');

      expect(spectator.service.healthExceptions()).toBeNull();
      expect(spectator.service.loadingExceptions()).toBeNull();
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
