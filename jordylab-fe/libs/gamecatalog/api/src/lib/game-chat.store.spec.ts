import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { ChatAskResponse } from './gamecatalog.models';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { GameChatStore } from './game-chat.store';
import { aChatAnswerMock } from './mocks/chat-answer.model.mock';
import { aGameDetailMock } from './mocks/game-detail.model.mock';

describe('GameChatStore', () => {
  let spectator: SpectatorService<GameChatStore>;
  const chat = vi.fn<GameCatalogApiService['chat']>();
  const getGame = vi.fn<GameCatalogApiService['getGame']>();

  const createService = createServiceFactory({
    service: GameChatStore,
    providers: [{ provide: GameCatalogApiService, useValue: { chat, getGame } }],
  });

  const unavailableMessage = {
    role: 'assistant',
    text: 'Chat is currently unavailable. Please try again later.',
    unavailable: true,
  };

  beforeEach(() => {
    chat.mockReset();
    getGame.mockReset();
    spectator = createService();
  });

  it('starts empty and idle', () => {
    expect(spectator.service.messages()).toEqual([]);
    expect(spectator.service.asking()).toBe(false);
    expect(spectator.service.attachedGame()).toBeNull();
  });

  it('appends the user question and the assistant answer with its games', () => {
    chat.mockReturnValue(of<ChatAskResponse>({ kind: 'answered', answer: aChatAnswerMock() }));

    spectator.service.ask('  which games support co-op?  ');

    expect(chat).toHaveBeenCalledWith('which games support co-op?', []);
    expect(spectator.service.messages()).toEqual([
      { role: 'user', text: 'which games support co-op?' },
      { role: 'assistant', text: aChatAnswerMock().answer, games: aChatAnswerMock().games },
    ]);
    expect(spectator.service.asking()).toBe(false);
  });

  it('sends attached game ids with the question', () => {
    const detail = aGameDetailMock({ id: 'game-1', title: 'Portal 2' });
    getGame.mockReturnValue(of(detail));
    chat.mockReturnValue(of<ChatAskResponse>({ kind: 'answered', answer: aChatAnswerMock() }));
    spectator.service.attachGame('game-1');

    spectator.service.ask('is this good for 4 players?');

    expect(chat).toHaveBeenCalledWith('is this good for 4 players?', ['game-1']);
  });

  it('loads and exposes the attached game', () => {
    getGame.mockReturnValue(of(aGameDetailMock({ id: 'game-1', title: 'Portal 2' })));

    spectator.service.attachGame('game-1');

    expect(getGame).toHaveBeenCalledWith('game-1');
    expect(spectator.service.attachedGame()).toEqual({ id: 'game-1', title: 'Portal 2' });
  });

  it('ignores a blank or duplicate attachment', () => {
    getGame.mockReturnValue(of(aGameDetailMock({ id: 'game-1', title: 'Portal 2' })));
    spectator.service.attachGame('');

    expect(getGame).not.toHaveBeenCalled();

    spectator.service.attachGame('game-1');
    spectator.service.attachGame('game-1');

    expect(getGame).toHaveBeenCalledTimes(1);
  });

  it('clears the attachment', () => {
    getGame.mockReturnValue(of(aGameDetailMock({ id: 'game-1', title: 'Portal 2' })));
    spectator.service.attachGame('game-1');

    spectator.service.clearAttachment();

    expect(spectator.service.attachedGame()).toBeNull();
  });

  it('is asking until the response arrives', () => {
    const response = new Subject<ChatAskResponse>();
    chat.mockReturnValue(response.asObservable());

    spectator.service.ask('co-op games?');

    expect(spectator.service.asking()).toBe(true);
    expect(spectator.service.messages()).toEqual([{ role: 'user', text: 'co-op games?' }]);

    response.next({ kind: 'answered', answer: aChatAnswerMock() });

    expect(spectator.service.asking()).toBe(false);
  });

  it('appends the unavailable message when the endpoint reports unavailable', () => {
    chat.mockReturnValue(of<ChatAskResponse>({ kind: 'unavailable' }));

    spectator.service.ask('co-op games?');

    expect(spectator.service.messages()[1]).toEqual(unavailableMessage);
    expect(spectator.service.asking()).toBe(false);
  });

  it('appends the unavailable message when the request errors', () => {
    chat.mockReturnValue(throwError(() => new Error('network error')));

    spectator.service.ask('co-op games?');

    expect(spectator.service.messages()[1]).toEqual(unavailableMessage);
    expect(spectator.service.asking()).toBe(false);
  });

  it('ignores blank questions', () => {
    spectator.service.ask('   ');

    expect(chat).not.toHaveBeenCalled();
    expect(spectator.service.messages()).toEqual([]);
  });

  it('ignores a question asked while another is pending', () => {
    chat.mockReturnValue(new Subject<ChatAskResponse>().asObservable());

    spectator.service.ask('first');
    spectator.service.ask('second');

    expect(chat).toHaveBeenCalledTimes(1);
    expect(spectator.service.messages()).toEqual([{ role: 'user', text: 'first' }]);
  });
});
