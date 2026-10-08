import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { LibBotAskEvent } from './gamecatalog.models';
import { LibBotStore } from './libbot.store';
import { aGameDetailMock } from './mocks/game-detail.model.mock';
import { aLibBotAnswerMock } from './mocks/libbot-answer.model.mock';
import { aLibBotQuotaMock } from './mocks/libbot-quota.model.mock';

describe('LibBotStore', () => {
  let spectator: SpectatorService<LibBotStore>;
  const askLibBot = vi.fn<GameCatalogApiService['askLibBot']>();
  const getLibBotQuota = vi.fn<GameCatalogApiService['getLibBotQuota']>();
  const forgetLibBotConversation = vi.fn<GameCatalogApiService['forgetLibBotConversation']>();
  const getGame = vi.fn<GameCatalogApiService['getGame']>();

  const createService = createServiceFactory({
    service: LibBotStore,
    providers: [
      { provide: GameCatalogApiService, useValue: { askLibBot, getLibBotQuota, forgetLibBotConversation, getGame } },
    ],
  });

  beforeEach(() => {
    askLibBot.mockReset();
    getLibBotQuota.mockReset();
    forgetLibBotConversation.mockReset();
    getGame.mockReset();
    getLibBotQuota.mockReturnValue(of(aLibBotQuotaMock()));
    forgetLibBotConversation.mockReturnValue(of(undefined));
    spectator = createService();
  });

  function stream(...events: LibBotAskEvent[]) {
    askLibBot.mockReturnValue(of(...events));
  }

  it('starts empty and idle', () => {
    expect(spectator.service.messages()).toEqual([]);
    expect(spectator.service.busy()).toBe(false);
    expect(spectator.service.failure()).toBeNull();
  });

  it('adds the question and the answer, and refreshes the allowance', () => {
    stream({ kind: 'stage', stage: 'UNDERSTANDING' }, { kind: 'answer', answer: aLibBotAnswerMock() });

    spectator.service.ask('  six people tonight  ');

    expect(spectator.service.messages()).toEqual([
      { role: 'user', text: 'six people tonight' },
      { role: 'assistant', text: aLibBotAnswerMock().text, answer: aLibBotAnswerMock() },
    ]);
    expect(spectator.service.busy()).toBe(false);
    expect(spectator.service.stage()).toBeNull();
    expect(getLibBotQuota).toHaveBeenCalled();
    expect(spectator.service.quota()).toEqual(aLibBotQuotaMock());
  });

  it('shows the real stage while LibBot works', () => {
    const events = new Subject<LibBotAskEvent>();
    askLibBot.mockReturnValue(events);

    spectator.service.ask('anything');
    expect(spectator.service.busy()).toBe(true);
    expect(spectator.service.stage()).toBe('UNDERSTANDING');

    events.next({ kind: 'stage', stage: 'SEARCHING' });
    expect(spectator.service.stage()).toBe('SEARCHING');
    events.next({ kind: 'stage', stage: 'WRITING' });
    expect(spectator.service.stage()).toBe('WRITING');
  });

  it('sends one conversation id for the whole visit and the attached game', () => {
    getGame.mockReturnValue(of(aGameDetailMock({ id: 'game-1', title: 'Portal 2' })));
    stream({ kind: 'answer', answer: aLibBotAnswerMock() });
    spectator.service.attachGame('game-1');

    spectator.service.ask('first');
    spectator.service.ask('second');

    const first = askLibBot.mock.calls[0][0];
    const second = askLibBot.mock.calls[1][0];
    expect(first.conversationId).toBe(second.conversationId);
    expect(first.attachedGameIds).toEqual(['game-1']);
    expect(first.message).toBe('first');
  });

  it('ignores a blank question and a question while one is running', () => {
    askLibBot.mockReturnValue(new Subject<LibBotAskEvent>());

    spectator.service.ask('   ');
    spectator.service.ask('first');
    spectator.service.ask('second');

    expect(askLibBot).toHaveBeenCalledTimes(1);
  });

  it('keeps the failed question and retries it without repeating it in the thread', () => {
    stream({ kind: 'error', code: 'UNAVAILABLE', retryable: true });
    spectator.service.ask('which games are installed?');

    expect(spectator.service.failure()).toEqual({ kind: 'unavailable', retryable: true });
    expect(spectator.service.failedQuestion()).toBe('which games are installed?');
    expect(spectator.service.busy()).toBe(false);

    stream({ kind: 'answer', answer: aLibBotAnswerMock() });
    spectator.service.retry();

    expect(askLibBot).toHaveBeenCalledTimes(2);
    expect(spectator.service.failure()).toBeNull();
    expect(spectator.service.messages().filter((message) => message.role === 'user')).toHaveLength(1);
    expect(spectator.service.messages()).toHaveLength(2);
  });

  it('reports the daily limit with its reset time and does not offer a retry', () => {
    stream({ kind: 'limitReached', resetsAt: '2026-10-08T00:00:00Z' });

    spectator.service.ask('one more');
    spectator.service.retry();

    expect(spectator.service.failure()).toEqual({ kind: 'limitReached', resetsAt: '2026-10-08T00:00:00Z' });
    expect(askLibBot).toHaveBeenCalledTimes(1);
  });

  it('starts a new conversation: clears the thread, asks the server to forget the old one and uses a new id', () => {
    stream({ kind: 'answer', answer: aLibBotAnswerMock() });
    spectator.service.ask('first');
    const oldId = askLibBot.mock.calls[0][0].conversationId;

    spectator.service.newConversation();
    spectator.service.ask('again');

    expect(forgetLibBotConversation).toHaveBeenCalledWith(oldId);
    expect(spectator.service.messages()).toHaveLength(2);
    expect(askLibBot.mock.calls[1][0].conversationId).not.toBe(oldId);
  });

  it('abandons a running question when a new conversation starts', () => {
    const events = new Subject<LibBotAskEvent>();
    askLibBot.mockReturnValue(events);
    spectator.service.ask('slow one');

    spectator.service.newConversation();

    expect(events.observed).toBe(false);
    expect(spectator.service.busy()).toBe(false);
    expect(spectator.service.messages()).toEqual([]);
  });

  it('keeps working when the allowance cannot be loaded', () => {
    getLibBotQuota.mockReturnValue(throwError(() => new Error('down')));

    spectator.service.loadQuota();

    expect(spectator.service.quota()).toBeNull();
  });

  it('loads and exposes the attached game, ignoring a blank or duplicate attachment', () => {
    getGame.mockReturnValue(of(aGameDetailMock({ id: 'game-1', title: 'Portal 2' })));

    spectator.service.attachGame('');
    spectator.service.attachGame('game-1');
    spectator.service.attachGame('game-1');

    expect(getGame).toHaveBeenCalledTimes(1);
    expect(spectator.service.attachedGame()?.title).toBe('Portal 2');

    spectator.service.clearAttachment();
    expect(spectator.service.attachedGame()).toBeNull();
  });
});
