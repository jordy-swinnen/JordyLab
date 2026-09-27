import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, ParamMap, RouterModule } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aAttachedGameMock, AttachedGame, ChatMessage, GameChatStore } from '@jordylab-fe/gamecatalog/api';
import { Subject } from 'rxjs';
import { GameChatComponent } from './game-chat.component';

describe('GameChatComponent', () => {
  const messages = signal<ChatMessage[]>([]);
  const asking = signal(false);
  const attachedGame = signal<AttachedGame | null>(null);
  const ask = vi.fn<GameChatStore['ask']>();
  const attachGame = vi.fn<GameChatStore['attachGame']>();
  const clearAttachment = vi.fn<GameChatStore['clearAttachment']>();

  const storeMock = {
    messages: messages.asReadonly(),
    asking: asking.asReadonly(),
    attachedGame: attachedGame.asReadonly(),
    ask,
    attachGame,
    clearAttachment,
  };

  const queryParamMap = new Subject<ParamMap>();

  let spectator: Spectator<GameChatComponent>;

  const createComponent = createComponentFactory({
    component: GameChatComponent,
    imports: [RouterModule.forRoot([])],
    providers: [
      { provide: GameChatStore, useValue: storeMock },
      { provide: ActivatedRoute, useValue: { queryParamMap: queryParamMap.asObservable() } },
    ],
  });

  const askQuestion = (question: string) => {
    const input = spectator.query('input[type="text"]') as HTMLInputElement;
    input.value = question;
    spectator.dispatchKeyboardEvent(input, 'keydown', 'Enter');
  };

  beforeEach(() => {
    messages.set([]);
    asking.set(false);
    attachedGame.set(null);
    ask.mockReset();
    attachGame.mockReset();
    clearAttachment.mockReset();
    spectator = createComponent();
  });

  it('attaches the game from the query param', () => {
    queryParamMap.next(convertToParamMap({ attach: 'game-1' }));
    spectator.detectChanges();

    expect(attachGame).toHaveBeenCalledWith('game-1');
  });

  it('re-attaches when the attach query param changes on a reused component', () => {
    queryParamMap.next(convertToParamMap({ attach: 'game-1' }));
    spectator.detectChanges();
    queryParamMap.next(convertToParamMap({ attach: 'game-2' }));
    spectator.detectChanges();

    expect(attachGame).toHaveBeenCalledTimes(2);
    expect(attachGame).toHaveBeenLastCalledWith('game-2');
  });

  it('shows an empty-state hint before the first question', () => {
    expect(spectator.element).toHaveText('Ask a question about your installed games.');
  });

  it('forwards the typed question to the store', () => {
    askQuestion('which games support local co-op?');

    expect(ask).toHaveBeenCalledWith('which games support local co-op?');
  });

  it('renders the attached game as a removable chip', () => {
    attachedGame.set(aAttachedGameMock({ id: 'game-1', title: 'Portal 2' }));
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Asking about Portal 2');

    const remove = spectator.query('button[aria-label="Remove attachment"]') as Element;
    spectator.click(remove);

    expect(clearAttachment).toHaveBeenCalled();
  });

  it('disables the remove button while a question is in flight', () => {
    attachedGame.set(aAttachedGameMock({ id: 'game-1', title: 'Portal 2' }));
    asking.set(true);
    spectator.detectChanges();

    const remove = spectator.query('button[aria-label="Remove attachment"]') as HTMLButtonElement;

    expect(remove.disabled).toBe(true);
  });

  it('renders user and assistant messages', () => {
    messages.set([
      { role: 'user', text: 'which games support local co-op?' },
      { role: 'assistant', text: 'One game supports local co-op.', games: [] },
    ]);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('which games support local co-op?');
    expect(spectator.element).toHaveText('One game supports local co-op.');
  });

  it('renders citations as links to the game detail', () => {
    messages.set([
      { role: 'user', text: 'co-op games?' },
      {
        role: 'assistant',
        text: 'Super Mario World supports 2-player co-op.',
        games: [
          {
            id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
            title: 'Super Mario World',
            platform: 'SNES',
            coverUrl: null,
            coverEndpoint: null,
          },
        ],
      },
    ]);
    spectator.detectChanges();

    const citation = spectator.query('a[href="/games/1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f"]');
    expect(citation).toBeTruthy();
    expect(citation).toHaveText('Super Mario World');
  });

  it('shows the thinking indicator while waiting for the answer', () => {
    messages.set([{ role: 'user', text: 'co-op games?' }]);
    asking.set(true);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Thinking…');
  });

  it('shows the unavailable state when the chat endpoint is down', () => {
    messages.set([
      { role: 'user', text: 'co-op games?' },
      { role: 'assistant', text: 'Chat is currently unavailable. Please try again later.', unavailable: true },
    ]);
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Chat is currently unavailable.');
  });

  it('renders **bold** markers from the model as emphasis instead of raw asterisks', () => {
    messages.set([
      { role: 'user', text: 'co-op games?' },
      { role: 'assistant', text: 'Try **For The King** (up to 3 players).', games: [] },
    ]);
    spectator.detectChanges();

    const bold = spectator.query('span.font-semibold');

    expect(bold).toHaveText('For The King');
    expect(spectator.element).not.toHaveText('**');
  });
});
