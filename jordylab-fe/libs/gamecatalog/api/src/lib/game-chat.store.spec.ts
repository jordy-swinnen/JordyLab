import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { of, Subject, throwError } from 'rxjs';
import { ChatAskResponse } from './gamecatalog.models';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { GameChatStore } from './game-chat.store';
import { aChatAnswerMock } from './mocks/chat-answer.model.mock';

describe('GameChatStore', () => {
  let spectator: SpectatorService<GameChatStore>;
  const chat = vi.fn<GameCatalogApiService['chat']>();

  const createService = createServiceFactory({
    service: GameChatStore,
    providers: [{ provide: GameCatalogApiService, useValue: { chat } }],
  });

  const unavailableMessage = {
    role: 'assistant',
    text: 'Chat is currently unavailable. Please try again later.',
    unavailable: true,
  };

  beforeEach(() => {
    chat.mockReset();
    spectator = createService();
  });

  it('starts empty and idle', () => {
    expect(spectator.service.messages()).toEqual([]);
    expect(spectator.service.asking()).toBe(false);
  });

  it('appends the user question and the assistant answer with its games', () => {
    chat.mockReturnValue(of<ChatAskResponse>({ kind: 'answered', answer: aChatAnswerMock() }));

    spectator.service.ask('  which games support co-op?  ');

    expect(chat).toHaveBeenCalledWith('which games support co-op?');
    expect(spectator.service.messages()).toEqual([
      { role: 'user', text: 'which games support co-op?' },
      { role: 'assistant', text: aChatAnswerMock().answer, games: aChatAnswerMock().games },
    ]);
    expect(spectator.service.asking()).toBe(false);
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
