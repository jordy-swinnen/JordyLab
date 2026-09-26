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

  const createService = createServiceFactory({
    service: GameDetailStore,
    providers: [{ provide: GameCatalogApiService, useValue: { getGame } }],
  });

  beforeEach(() => {
    getGame.mockReset();
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
});
