import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { ScanSource } from './gamecatalog.models';
import { ScanSourceStore } from './scan-source.store';
import { aScanSourceMock } from './mocks/scan-source.model.mock';

describe('ScanSourceStore', () => {
  let spectator: SpectatorService<ScanSourceStore>;
  const getSources = vi.fn<GameCatalogApiService['getSources']>();
  const setSourceEnabled = vi.fn<GameCatalogApiService['setSourceEnabled']>();
  const getScanScript = vi.fn<GameCatalogApiService['getScanScript']>();

  const createService = createServiceFactory({
    service: ScanSourceStore,
    providers: [{ provide: GameCatalogApiService, useValue: { getSources, setSourceEnabled, getScanScript } }],
  });

  beforeEach(() => {
    getSources.mockReset();
    getSources.mockReturnValue(of([aScanSourceMock()]));
    setSourceEnabled.mockReset();
    setSourceEnabled.mockImplementation((id, enabled) => of({ id, enabled }));
    getScanScript.mockReset();
    getScanScript.mockReturnValue(of(new Blob(['#!/bin/sh'])));
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

  describe('downloadScript', () => {
    const onReady = vi.fn<(blob: Blob) => void>();

    beforeEach(() => {
      onReady.mockReset();
      spectator = createService();
    });

    it('hands the generated script to the callback', () => {
      const blob = new Blob(['#!/bin/sh']);
      getScanScript.mockReturnValue(of(blob));

      spectator.service.downloadScript('steam', onReady);

      expect(getScanScript).toHaveBeenCalledWith('steam');
      expect(onReady).toHaveBeenCalledWith(blob);
      expect(spectator.service.downloading()).toBeNull();
    });

    it('marks the library as downloading until the script arrives', () => {
      const script = new Subject<Blob>();
      getScanScript.mockReturnValue(script.asObservable());

      spectator.service.downloadScript('emudeck', onReady);

      expect(spectator.service.downloading()).toBe('emudeck');

      script.next(new Blob([]));

      expect(spectator.service.downloading()).toBeNull();
    });

    it('ignores a download while another is in flight', () => {
      getScanScript.mockReturnValue(new Subject<Blob>().asObservable());

      spectator.service.downloadScript('steam', onReady);
      spectator.service.downloadScript('emudeck', onReady);

      expect(getScanScript).toHaveBeenCalledTimes(1);
    });

    it('reports a failure without calling the callback', () => {
      getScanScript.mockReturnValue(throwError(() => new Error('network error')));

      spectator.service.downloadScript('steam', onReady);

      expect(spectator.service.error()).toBe('Failed to generate steam scan script.');
      expect(spectator.service.downloading()).toBeNull();
      expect(onReady).not.toHaveBeenCalled();
    });
  });
});
