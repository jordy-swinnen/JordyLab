import { HttpErrorResponse } from '@angular/common/http';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { GameDetail } from './gamecatalog.models';
import { GameDetailStore } from './game-detail.store';
import { aGameDetailMock } from './mocks/game-detail.model.mock';

describe('GameDetailStore', () => {
  let spectator: SpectatorService<GameDetailStore>;
  const getGame = vi.fn<GameCatalogApiService['getGame']>();
  const refreshGameMetadata = vi.fn<GameCatalogApiService['refreshGameMetadata']>();
  const refreshGameEnrichment = vi.fn<GameCatalogApiService['refreshGameEnrichment']>();
  const setRomStatus = vi.fn<GameCatalogApiService['setRomStatus']>();

  const createService = createServiceFactory({
    service: GameDetailStore,
    providers: [{ provide: GameCatalogApiService, useValue: { getGame, refreshGameMetadata, refreshGameEnrichment, setRomStatus } }],
  });

  beforeEach(() => {
    getGame.mockReset();
    refreshGameMetadata.mockReset();
    refreshGameEnrichment.mockReset();
    setRomStatus.mockReset();
    spectator = createService();
  });

  it('starts in the loading state', () => {
    expect(spectator.service.loading()).toBe(true);
    expect(spectator.service.game()).toBeNull();
  });

  it('loads the game for the id', () => {
    getGame.mockReturnValue(of(aGameDetailMock()));

    spectator.service.load('abc');

    expect(getGame).toHaveBeenCalledWith('abc');
    expect(spectator.service.game()).toEqual(aGameDetailMock());
    expect(spectator.service.loading()).toBe(false);
    expect(spectator.service.notFound()).toBe(false);
    expect(spectator.service.error()).toBeNull();
  });

  it('is loading until the game arrives', () => {
    const game = new Subject<GameDetail>();
    getGame.mockReturnValue(game.asObservable());

    spectator.service.load('abc');

    expect(spectator.service.loading()).toBe(true);

    game.next(aGameDetailMock());

    expect(spectator.service.loading()).toBe(false);
  });

  it('flags a 404 as not found without an error message', () => {
    getGame.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

    spectator.service.load('abc');

    expect(spectator.service.notFound()).toBe(true);
    expect(spectator.service.error()).toBeNull();
    expect(spectator.service.loading()).toBe(false);
  });

  it('sets an error message for other failures', () => {
    getGame.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    spectator.service.load('abc');

    expect(spectator.service.error()).toBe('Failed to load the game.');
    expect(spectator.service.notFound()).toBe(false);
    expect(spectator.service.loading()).toBe(false);
  });

  it('discards the previous game and state when loading another one', () => {
    getGame.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })));
    spectator.service.load('missing');
    getGame.mockReturnValue(of(aGameDetailMock()));

    spectator.service.load('abc');

    expect(spectator.service.notFound()).toBe(false);
    expect(spectator.service.game()).toEqual(aGameDetailMock());
  });

  it('clears the previous game while the next one loads', () => {
    getGame.mockReturnValueOnce(of(aGameDetailMock()));
    spectator.service.load('abc');
    getGame.mockReturnValue(new Subject<GameDetail>().asObservable());

    spectator.service.load('def');

    expect(spectator.service.game()).toBeNull();
    expect(spectator.service.loading()).toBe(true);
  });

  it('refreshes deterministic metadata and replaces the loaded game', () => {
    getGame.mockReturnValue(of(aGameDetailMock()));
    spectator.service.load('abc');
    const refreshed = aGameDetailMock({ developer: 'Valve', releaseYear: 2011 });
    refreshGameMetadata.mockReturnValue(of(refreshed));

    spectator.service.refreshMetadata();

    expect(refreshGameMetadata).toHaveBeenCalledWith(aGameDetailMock().id);
    expect(spectator.service.game()).toEqual(refreshed);
    expect(spectator.service.refreshingMetadata()).toBe(false);
  });

  it('is refreshing until the metadata refresh arrives', () => {
    getGame.mockReturnValue(of(aGameDetailMock()));
    spectator.service.load('abc');
    refreshGameMetadata.mockReturnValue(new Subject<GameDetail>().asObservable());

    spectator.service.refreshMetadata();

    expect(spectator.service.refreshingMetadata()).toBe(true);
  });

  it('sets an error when the metadata refresh fails', () => {
    getGame.mockReturnValue(of(aGameDetailMock()));
    spectator.service.load('abc');
    refreshGameMetadata.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    spectator.service.refreshMetadata();

    expect(spectator.service.error()).toBe('Failed to refresh the deterministic facts.');
    expect(spectator.service.refreshingMetadata()).toBe(false);
  });

  it('refreshes the AI description and replaces the loaded game', () => {
    getGame.mockReturnValue(of(aGameDetailMock()));
    spectator.service.load('abc');
    const regenerated = aGameDetailMock({ description: 'A regenerated description.' });
    refreshGameEnrichment.mockReturnValue(of(regenerated));

    spectator.service.refreshEnrichment();

    expect(refreshGameEnrichment).toHaveBeenCalledWith(aGameDetailMock().id);
    expect(spectator.service.game()).toEqual(regenerated);
    expect(spectator.service.refreshingEnrichment()).toBe(false);
  });

  it('sets an error when the enrichment refresh fails', () => {
    getGame.mockReturnValue(of(aGameDetailMock()));
    spectator.service.load('abc');
    refreshGameEnrichment.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    spectator.service.refreshEnrichment();

    expect(spectator.service.error()).toBe('Failed to regenerate the description.');
  });

  it('does not refresh when no game is loaded', () => {
    spectator.service.refreshMetadata();
    spectator.service.refreshEnrichment();

    expect(refreshGameMetadata).not.toHaveBeenCalled();
    expect(refreshGameEnrichment).not.toHaveBeenCalled();
  });

  describe('ROM status', () => {
    const detail = aGameDetailMock();
    const copyId = detail.places[0].installationId as string;

    beforeEach(() => {
      getGame.mockReturnValue(of(detail));
      spectator.service.load('abc');
    });

    it('shows the saved place instead of guessing', () => {
      setRomStatus.mockReturnValue(of({ ...detail.places[0], romStatus: 'BROKEN' }));

      spectator.service.setRomStatus(copyId, 'BROKEN');

      expect(setRomStatus).toHaveBeenCalledWith(detail.id, copyId, 'BROKEN');
      expect(spectator.service.game()?.places[0].romStatus).toBe('BROKEN');
      expect(spectator.service.savingRomStatus().size).toBe(0);
    });

    it('marks the copy as saving while the request is out and ignores a second tap', () => {
      const answer = new Subject<GameDetail['places'][number]>();
      setRomStatus.mockReturnValue(answer.asObservable());

      spectator.service.setRomStatus(copyId, 'VALIDATED');
      spectator.service.setRomStatus(copyId, 'BROKEN');

      expect(setRomStatus).toHaveBeenCalledTimes(1);
      expect(spectator.service.savingRomStatus().has(copyId)).toBe(true);
    });

    it('keeps the old status and says so when saving fails', () => {
      setRomStatus.mockReturnValue(throwError(() => new Error('offline')));

      spectator.service.setRomStatus(copyId, 'BROKEN');

      expect(spectator.service.game()?.places[0].romStatus).toBe('UNKNOWN');
      expect(spectator.service.romStatusError()).toBe('Could not save the ROM status. Try again.');
      spectator.service.dismissRomStatusError();
      expect(spectator.service.romStatusError()).toBeNull();
    });
  });
});
