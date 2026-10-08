import { HttpErrorResponse } from '@angular/common/http';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { RefreshRun } from './gamecatalog.models';
import { aRefreshRunMock } from './mocks/refresh-run.model.mock';
import { RefreshRunStore } from './refresh-run.store';

describe('RefreshRunStore', () => {
  let spectator: SpectatorService<RefreshRunStore>;
  const startRefreshRun = vi.fn<GameCatalogApiService['startRefreshRun']>();
  const getCurrentRefreshRun = vi.fn<GameCatalogApiService['getCurrentRefreshRun']>();
  const stopRefreshRun = vi.fn<GameCatalogApiService['stopRefreshRun']>();

  const createService = createServiceFactory({
    service: RefreshRunStore,
    providers: [{ provide: GameCatalogApiService, useValue: { startRefreshRun, getCurrentRefreshRun, stopRefreshRun } }],
  });

  beforeEach(() => {
    vi.useFakeTimers();
    startRefreshRun.mockReset();
    getCurrentRefreshRun.mockReset();
    getCurrentRefreshRun.mockReturnValue(of(null));
    stopRefreshRun.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows the latest run of each kind when it is created, and nothing for a kind never run', () => {
    getCurrentRefreshRun.mockImplementation((kind) => of(kind === 'AI' ? aRefreshRunMock({ kind: 'AI', status: 'SUCCEEDED' }) : null));

    spectator = createService();

    expect(spectator.service.dataRun()).toBeNull();
    expect(spectator.service.aiRun()?.status).toBe('SUCCEEDED');
  });

  it('starts a data run without any confirmation', () => {
    spectator = createService();
    startRefreshRun.mockReturnValue(of(aRefreshRunMock()));

    spectator.service.startData();

    expect(startRefreshRun).toHaveBeenCalledWith('DATA', false);
    expect(spectator.service.dataRun()?.status).toBe('RUNNING');
  });

  it('asks the server what the AI run costs and opens the confirmation with the game count', () => {
    spectator = createService();
    startRefreshRun.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { reason: 'COST_CONFIRMATION_REQUIRED', games: 228 } })),
    );

    spectator.service.requestAi();

    expect(spectator.service.aiCostQuote()).toBe(228);
    expect(spectator.service.aiRun()).toBeNull();
  });

  it('starts the AI run only once confirmed and closes the dialog', () => {
    spectator = createService();
    startRefreshRun.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { reason: 'COST_CONFIRMATION_REQUIRED', games: 5 } })),
    );
    spectator.service.requestAi();
    startRefreshRun.mockReturnValue(of(aRefreshRunMock({ kind: 'AI' })));

    spectator.service.confirmAi();

    expect(startRefreshRun).toHaveBeenLastCalledWith('AI', true);
    expect(spectator.service.aiCostQuote()).toBeNull();
    expect(spectator.service.aiRun()?.kind).toBe('AI');
  });

  it('closes the dialog on cancel without starting anything', () => {
    spectator = createService();
    startRefreshRun.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { reason: 'COST_CONFIRMATION_REQUIRED', games: 5 } })),
    );
    spectator.service.requestAi();
    startRefreshRun.mockClear();

    spectator.service.cancelAi();

    expect(spectator.service.aiCostQuote()).toBeNull();
    expect(startRefreshRun).not.toHaveBeenCalled();
  });

  it('says a refresh is already running and reloads the current run when the server refuses a second start', () => {
    spectator = createService();
    startRefreshRun.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { reason: 'RUN_ALREADY_ACTIVE' } })),
    );
    getCurrentRefreshRun.mockClear();

    spectator.service.startData();

    expect(spectator.service.error()).toBe('A refresh of this kind is already running.');
    expect(getCurrentRefreshRun).toHaveBeenCalledWith('DATA');
  });

  it('gives a plain message for any other failure', () => {
    spectator = createService();
    startRefreshRun.mockReturnValue(throwError(() => new Error('offline')));

    spectator.service.startData();

    expect(spectator.service.error()).toBe('Could not start the game data refresh.');
    spectator.service.dismissError();
    expect(spectator.service.error()).toBeNull();
  });

  it('polls every two seconds while a run is running and stops once it has finished', () => {
    const running = aRefreshRunMock({ processed: 10 });
    getCurrentRefreshRun.mockImplementation((kind) => of(kind === 'DATA' ? running : null));
    spectator = createService();
    getCurrentRefreshRun.mockClear();
    getCurrentRefreshRun.mockImplementation((kind) =>
      of(kind === 'DATA' ? aRefreshRunMock({ processed: 40 }) : null),
    );

    vi.advanceTimersByTime(2000);

    expect(getCurrentRefreshRun).toHaveBeenCalledTimes(1);
    expect(spectator.service.dataRun()?.processed).toBe(40);

    getCurrentRefreshRun.mockClear();
    getCurrentRefreshRun.mockImplementation(() => of(aRefreshRunMock({ status: 'SUCCEEDED', processed: 228 })));
    vi.advanceTimersByTime(2000);
    expect(spectator.service.dataRun()?.status).toBe('SUCCEEDED');

    getCurrentRefreshRun.mockClear();
    vi.advanceTimersByTime(10000);
    expect(getCurrentRefreshRun).not.toHaveBeenCalled();
  });

  it('keeps the last known run when a poll fails', () => {
    getCurrentRefreshRun.mockImplementation((kind) => of(kind === 'DATA' ? aRefreshRunMock() : null));
    spectator = createService();
    getCurrentRefreshRun.mockImplementation(() => throwError(() => new Error('offline')));

    vi.advanceTimersByTime(2000);

    expect(spectator.service.dataRun()?.processed).toBe(91);
  });

  it('stops a run and shows the run with its stop flag', () => {
    spectator = createService();
    const running = aRefreshRunMock();
    stopRefreshRun.mockReturnValue(of({ ...running, stopRequested: true }));

    spectator.service.stop(running);

    expect(stopRefreshRun).toHaveBeenCalledWith(running.id);
    expect(spectator.service.dataRun()?.stopRequested).toBe(true);
  });

  it('says so when the stop request fails', () => {
    spectator = createService();
    stopRefreshRun.mockReturnValue(throwError(() => new Error('offline')));

    spectator.service.stop(aRefreshRunMock());

    expect(spectator.service.error()).toBe('Could not stop the refresh.');
  });

  it('ignores a second start while one is being sent', () => {
    spectator = createService();
    const answer = new Subject<RefreshRun>();
    startRefreshRun.mockReturnValue(answer.asObservable());

    spectator.service.startData();
    spectator.service.startData();

    expect(startRefreshRun).toHaveBeenCalledTimes(1);
    expect(spectator.service.starting()).toBe('DATA');
  });
});
