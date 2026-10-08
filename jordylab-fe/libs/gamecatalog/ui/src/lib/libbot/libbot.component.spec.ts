import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, ParamMap, RouterModule } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import {
  aAttachedGameMock,
  aLibBotAnswerMock,
  aLibBotQuotaMock,
  AttachedGame,
  LibBotFailure,
  LibBotMessage,
  LibBotQuota,
  LibBotStage,
  LibBotStore,
} from '@jordylab-fe/gamecatalog/api';
import { Subject } from 'rxjs';
import { LibBotComponent } from './libbot.component';

describe('LibBotComponent', () => {
  const messages = signal<LibBotMessage[]>([]);
  const busy = signal(false);
  const stage = signal<LibBotStage | null>(null);
  const failure = signal<LibBotFailure | null>(null);
  const failedQuestion = signal<string | null>(null);
  const quota = signal<LibBotQuota | null>(null);
  const attachedGame = signal<AttachedGame | null>(null);
  const ask = vi.fn<LibBotStore['ask']>();
  const retry = vi.fn<LibBotStore['retry']>();
  const newConversation = vi.fn<LibBotStore['newConversation']>();
  const attachGame = vi.fn<LibBotStore['attachGame']>();
  const clearAttachment = vi.fn<LibBotStore['clearAttachment']>();
  const loadQuota = vi.fn<LibBotStore['loadQuota']>();

  const storeMock = {
    messages: messages.asReadonly(),
    busy: busy.asReadonly(),
    stage: stage.asReadonly(),
    failure: failure.asReadonly(),
    failedQuestion: failedQuestion.asReadonly(),
    quota: quota.asReadonly(),
    attachedGame: attachedGame.asReadonly(),
    ask,
    retry,
    newConversation,
    attachGame,
    clearAttachment,
    loadQuota,
  };

  const queryParamMap = new Subject<ParamMap>();

  let spectator: Spectator<LibBotComponent>;

  const createComponent = createComponentFactory({
    component: LibBotComponent,
    imports: [RouterModule.forRoot([])],
    providers: [
      { provide: LibBotStore, useValue: storeMock },
      { provide: ActivatedRoute, useValue: { queryParamMap: queryParamMap.asObservable() } },
    ],
  });

  const input = () => spectator.query('input[type="text"]') as HTMLInputElement;
  const askQuestion = (question: string) => {
    input().value = question;
    spectator.dispatchKeyboardEvent(input(), 'keydown', 'Enter');
  };

  beforeEach(() => {
    messages.set([]);
    busy.set(false);
    stage.set(null);
    failure.set(null);
    failedQuestion.set(null);
    quota.set(null);
    attachedGame.set(null);
    [ask, retry, newConversation, attachGame, clearAttachment, loadQuota].forEach((fn) => fn.mockReset());
    spectator = createComponent();
  });

  it('loads the allowance when the page opens', () => {
    expect(loadQuota).toHaveBeenCalled();
  });

  it('attaches the game from the query param and re-attaches when it changes', () => {
    queryParamMap.next(convertToParamMap({ attach: 'game-1' }));
    spectator.detectChanges();
    queryParamMap.next(convertToParamMap({ attach: 'game-2' }));
    spectator.detectChanges();

    expect(attachGame).toHaveBeenCalledWith('game-1');
    expect(attachGame).toHaveBeenLastCalledWith('game-2');
  });

  it('shows a hint in both languages before the first question', () => {
    expect(spectator.element).toHaveText('English or Nederlands');
  });

  it('pre-fills the question box from the prefill query param', () => {
    queryParamMap.next(convertToParamMap({ prefill: 'https://example.com/shared-article' }));
    spectator.detectChanges();

    expect(input().value).toBe('https://example.com/shared-article');
  });

  it('forwards the typed question and clears the box', () => {
    askQuestion('which games support local co-op?');

    expect(ask).toHaveBeenCalledWith('which games support local co-op?');
    expect(input().value).toBe('');
  });

  it('does not send a blank question', () => {
    askQuestion('   ');

    expect(ask).not.toHaveBeenCalled();
  });

  it('renders the answer with the applied chips, the unknown-data note and the reference links', () => {
    messages.set([
      { role: 'user', text: 'six people tonight' },
      {
        role: 'assistant',
        text: 'Super Mario World works.',
        answer: aLibBotAnswerMock({
          text: 'Super Mario World works.',
          unknown: { count: 3, note: 'For 3 more games the player count isn’t known yet.' },
        }),
      },
    ]);
    spectator.detectChanges();

    expect(spectator.query('[data-testid="libbot-user-message"]')).toHaveText('six people tonight');
    expect(spectator.query('[data-testid="libbot-unknown-note"]')).toHaveText('For 3 more games');
    expect(spectator.query('[data-testid="libbot-applied"]')).toHaveText('2+ local players');
    const link = spectator.query('[data-testid="libbot-references"] a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/games/1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f');
    expect(link).toHaveText('Super Mario World');
  });

  it('shows no reference or applied row for a decline', () => {
    messages.set([
      { role: 'assistant', text: 'That is outside what I can help with.', answer: aLibBotAnswerMock({
        outcome: 'OUT_OF_SCOPE', text: 'That is outside what I can help with.', applied: [], references: [],
      }) },
    ]);
    spectator.detectChanges();

    expect(spectator.query('[data-testid="libbot-references"]')).toBeNull();
    expect(spectator.query('[data-testid="libbot-applied"]')).toBeNull();
  });

  it('shows the live stages as a status region while working', () => {
    busy.set(true);
    stage.set('SEARCHING');
    spectator.detectChanges();

    const stages = spectator.query('[data-testid="libbot-stages"]') as HTMLElement;
    expect(stages.getAttribute('role')).toBe('status');
    expect(stages).toHaveText('Understood');
    expect(stages).toHaveText('Searching your library');
    expect(stages.querySelector('[aria-current="step"]')).toHaveText('Searching your library');
    expect(input().disabled).toBe(true);
  });

  it('offers Try again after a failure and puts the failed question back in the box', () => {
    failure.set({ kind: 'unavailable', retryable: true });
    failedQuestion.set('which games are installed?');
    spectator.detectChanges();

    expect(spectator.query('[data-testid="libbot-error"]')).toHaveText('Nothing was counted');
    expect(input().value).toBe('which games are installed?');
    spectator.click('[data-testid="libbot-retry"]');
    expect(retry).toHaveBeenCalled();
  });

  it('replaces the composer with the reset time when the daily limit is reached', () => {
    failure.set({ kind: 'limitReached', resetsAt: '2026-10-08T00:00:00Z' });
    spectator.detectChanges();

    expect(spectator.query('[data-testid="libbot-limit"]')).toHaveText('Limit reached, resets at');
    expect(spectator.query('input[type="text"]')).toBeNull();
  });

  it('shows the remaining messages for a guest and nothing for an admin', () => {
    quota.set(aLibBotQuotaMock({ limit: 20, remaining: 14 }));
    spectator.detectChanges();
    expect(spectator.query('[data-testid="libbot-quota"]')).toHaveText('14 of 20 messages left today');

    quota.set(aLibBotQuotaMock({ limit: null, remaining: null, exempt: true }));
    spectator.detectChanges();
    expect(spectator.query('[data-testid="libbot-quota"]')).toBeNull();
  });

  it('starts a new conversation from the button', () => {
    spectator.click('[data-testid="libbot-new-conversation"]');

    expect(newConversation).toHaveBeenCalled();
  });

  it('renders the attached game as a removable chip', () => {
    attachedGame.set(aAttachedGameMock({ id: 'game-1', title: 'Portal 2' }));
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Asking about Portal 2');
    spectator.click('button[aria-label="Remove attachment"]');
    expect(clearAttachment).toHaveBeenCalled();
  });
});
