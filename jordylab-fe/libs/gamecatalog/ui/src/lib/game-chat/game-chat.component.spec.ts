import { RouterModule } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { Subject } from 'rxjs';
import { ChatAskResponse, GameCatalogApiService } from '@jordylab-fe/gamecatalog/api';
import { GameChatComponent } from './game-chat.component';

class GameCatalogApiServiceMock {
  private chatSubject = new Subject<ChatAskResponse>();
  chat = vi.fn(() => this.chatSubject.asObservable());

  answer(text: string, games: { id: string; title: string; platform: string }[] = []) {
    this.chatSubject.next({ kind: 'answered', answer: { answer: text, games, noMatch: games.length === 0 } });
  }

  unavailable() {
    this.chatSubject.next({ kind: 'unavailable' });
  }
}

describe('GameChatComponent', () => {
  const createComponent = createComponentFactory({
    component: GameChatComponent,
    imports: [RouterModule.forRoot([])],
    providers: [{ provide: GameCatalogApiService, useClass: GameCatalogApiServiceMock }],
  });

  const apiMock = (spectator: Spectator<GameChatComponent>) =>
    spectator.inject(GameCatalogApiService) as unknown as GameCatalogApiServiceMock;

  const askQuestion = (spectator: Spectator<GameChatComponent>, question: string) => {
    const input = spectator.query('input[type="text"]') as HTMLInputElement;
    input.value = question;
    spectator.dispatchKeyboardEvent(input, 'keydown', 'Enter');
  };

  it('shows an empty-state hint before the first question', () => {
    const spectator: Spectator<GameChatComponent> = createComponent();

    expect(spectator.element).toHaveText('Ask a question about your installed games.');
  });

  it('sends a question and renders user and assistant messages', () => {
    const spectator: Spectator<GameChatComponent> = createComponent();
    askQuestion(spectator, 'which games support local co-op?');
    spectator.detectChanges();

    expect(apiMock(spectator).chat).toHaveBeenCalledWith('which games support local co-op?');
    expect(spectator.element).toHaveText('which games support local co-op?');

    apiMock(spectator).answer('One game supports local co-op.');
    spectator.detectChanges();

    expect(spectator.element).toHaveText('One game supports local co-op.');
  });

  it('renders citations as links to the game detail', () => {
    const spectator: Spectator<GameChatComponent> = createComponent();
    askQuestion(spectator, 'co-op games?');
    apiMock(spectator).answer('Super Mario World supports 2-player co-op.', [
      { id: '1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f', title: 'Super Mario World', platform: 'SNES' },
    ]);
    spectator.detectChanges();

    const citation = spectator.query('a[href="/games/1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f"]');
    expect(citation).toBeTruthy();
    expect(citation).toHaveText('Super Mario World');
  });

  it('shows the thinking indicator while waiting for the answer', () => {
    const spectator: Spectator<GameChatComponent> = createComponent();
    askQuestion(spectator, 'co-op games?');
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Thinking…');
  });

  it('shows the unavailable state when the chat endpoint is down', () => {
    const spectator: Spectator<GameChatComponent> = createComponent();
    askQuestion(spectator, 'co-op games?');
    apiMock(spectator).unavailable();
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Chat is currently unavailable.');
  });

  it('ignores blank questions', () => {
    const spectator: Spectator<GameChatComponent> = createComponent();
    askQuestion(spectator, '   ');

    expect(apiMock(spectator).chat).not.toHaveBeenCalled();
    expect(spectator.element).toHaveText('Ask a question about your installed games.');
  });
});
