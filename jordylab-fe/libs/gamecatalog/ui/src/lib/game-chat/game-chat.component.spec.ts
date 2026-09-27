import { signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { ChatMessage, GameChatStore } from '@jordylab-fe/gamecatalog/api';
import { GameChatComponent } from './game-chat.component';

describe('GameChatComponent', () => {
  const messages = signal<ChatMessage[]>([]);
  const asking = signal(false);
  const ask = vi.fn<GameChatStore['ask']>();

  const storeMock = { messages: messages.asReadonly(), asking: asking.asReadonly(), ask };

  let spectator: Spectator<GameChatComponent>;

  const createComponent = createComponentFactory({
    component: GameChatComponent,
    imports: [RouterModule.forRoot([])],
    providers: [{ provide: GameChatStore, useValue: storeMock }],
  });

  const askQuestion = (question: string) => {
    const input = spectator.query('input[type="text"]') as HTMLInputElement;
    input.value = question;
    spectator.dispatchKeyboardEvent(input, 'keydown', 'Enter');
  };

  beforeEach(() => {
    messages.set([]);
    asking.set(false);
    ask.mockReset();
    spectator = createComponent();
  });

  it('shows an empty-state hint before the first question', () => {
    expect(spectator.element).toHaveText('Ask a question about your installed games.');
  });

  it('forwards the typed question to the store', () => {
    askQuestion('which games support local co-op?');

    expect(ask).toHaveBeenCalledWith('which games support local co-op?');
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
        games: [{ id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f', title: 'Super Mario World', platform: 'SNES' }],
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
