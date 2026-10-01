import { computed, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import {
  aSwitchSearchResultMock,
  SwitchGameFormat,
  SwitchGameStore,
  SwitchSearchResult,
} from '@jordylab-fe/gamecatalog/api';
import { SwitchGameComponent } from './switch-game.component';

describe('SwitchGameComponent', () => {
  const results = signal<SwitchSearchResult[]>([]);
  const selectedResult = signal<SwitchSearchResult | null>(null);
  const manualTitle = signal('');
  const format = signal<SwitchGameFormat>('PHYSICAL');
  const mode = signal<'search' | 'manual'>('search');
  const loading = signal(false);
  const error = signal<string | null>(null);
  const adding = signal(false);
  const addError = signal<string | null>(null);
  const added = signal(false);
  const noMatchFor = signal<string | null>(null);
  const search = vi.fn<SwitchGameStore['search']>();
  const selectResult = vi.fn<SwitchGameStore['selectResult']>();
  const addNoMatchManually = vi.fn<SwitchGameStore['addNoMatchManually']>();
  const addGame = vi.fn<SwitchGameStore['addGame']>();
  const setMode = vi.fn<SwitchGameStore['setMode']>();

  const storeMock = {
    results: results.asReadonly(),
    selectedResult: selectedResult.asReadonly(),
    manualTitle: manualTitle.asReadonly(),
    format: format.asReadonly(),
    mode: mode.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    adding: adding.asReadonly(),
    addError: addError.asReadonly(),
    added: added.asReadonly(),
    noMatchFor: noMatchFor.asReadonly(),
    canAdd: computed(() => selectedResult() != null),
    search,
    selectResult,
    addNoMatchManually,
    addGame,
    setMode,
    setManualTitle: vi.fn<SwitchGameStore['setManualTitle']>(),
    setFormat: vi.fn<SwitchGameStore['setFormat']>(),
    reset: vi.fn<SwitchGameStore['reset']>(),
  };

  let spectator: Spectator<SwitchGameComponent>;

  const createComponent = createComponentFactory({
    component: SwitchGameComponent,
    providers: [provideRouter([]), { provide: SwitchGameStore, useValue: storeMock }],
  });

  beforeEach(() => {
    results.set([]);
    selectedResult.set(null);
    mode.set('search');
    noMatchFor.set(null);
    added.set(false);
    [search, selectResult, addNoMatchManually, addGame, setMode].forEach((mock) => mock.mockReset());
    spectator = createComponent();
  });

  it('searches IGDB as the admin types', () => {
    spectator.typeInElement('Mario Kart', '#switch-search');

    expect(search).toHaveBeenCalledWith('Mario Kart');
  });

  it('selects a result with the keyboard as well as the mouse', () => {
    results.set([aSwitchSearchResultMock({ title: 'Mario Kart 8 Deluxe' })]);
    spectator.detectChanges();

    spectator.dispatchKeyboardEvent('[role="button"]', 'keydown', 'Enter');

    expect(selectResult).toHaveBeenCalledWith(aSwitchSearchResultMock({ title: 'Mario Kart 8 Deluxe' }));
  });

  it('offers to add the searched title manually when IGDB has no match', () => {
    noMatchFor.set('My Indie Game');
    spectator.detectChanges();

    expect(spectator.query('[data-testid="switch-no-match"]')).toContainText('My Indie Game');

    spectator.click('[data-testid="switch-add-manually"]');

    expect(addNoMatchManually).toHaveBeenCalled();
  });

  it('shows no no-match prompt while results exist', () => {
    results.set([aSwitchSearchResultMock()]);
    spectator.detectChanges();

    expect(spectator.query('[data-testid="switch-no-match"]')).toBeNull();
  });

  it('adds the selected game', () => {
    selectedResult.set(aSwitchSearchResultMock());
    spectator.detectChanges();

    spectator.click(spectator.queryAll('button').find((button) => button.textContent?.includes('Add Game')) as HTMLElement);

    expect(addGame).toHaveBeenCalled();
  });
});
