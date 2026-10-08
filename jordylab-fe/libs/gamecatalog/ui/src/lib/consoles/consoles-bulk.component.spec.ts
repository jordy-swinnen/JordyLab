import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { of } from 'rxjs';
import {
  aConsoleMock,
  aConsoleSearchResultMock,
  ConsoleBulkLine,
  ConsoleBulkSummary,
  ConsoleStore,
  GameConsole,
} from '@jordylab-fe/gamecatalog/api';
import { ConsolesBulkComponent } from './consoles-bulk.component';

describe('ConsolesBulkComponent', () => {
  const lines = signal<ConsoleBulkLine[]>([]);
  const summary = signal<ConsoleBulkSummary | null>(null);
  const busy = signal(false);
  const error = signal<string | null>(null);
  const consoles = signal<GameConsole[]>([]);
  const selectedId = signal<string | null>(null);
  const selected = signal<GameConsole | null>(null);
  const previewBulk = vi.fn();
  const confirmBulk = vi.fn();
  const resetBulk = vi.fn();
  const load = vi.fn();
  const selectConsole = vi.fn();

  const storeMock = {
    bulkLines: lines.asReadonly(),
    bulkSummary: summary.asReadonly(),
    busy: busy.asReadonly(),
    error: error.asReadonly(),
    consoles: consoles.asReadonly(),
    selectedId: selectedId.asReadonly(),
    selected: selected.asReadonly(),
    previewBulk,
    confirmBulk,
    resetBulk,
    load,
    selectConsole,
  };

  let spectator: Spectator<ConsolesBulkComponent>;

  const createComponent = createComponentFactory({
    component: ConsolesBulkComponent,
    providers: [
      provideRouter([]),
      { provide: ConsoleStore, useValue: storeMock },
      { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'dock-id' })) } },
    ],
  });

  beforeEach(() => {
    lines.set([]);
    summary.set(null);
    busy.set(false);
    error.set(null);
    consoles.set([aConsoleMock({ id: 'dock-id' })]);
    selectedId.set(null);
    selected.set(aConsoleMock({ id: 'dock-id' }));
    [previewBulk, confirmBulk, resetBulk, load, selectConsole].forEach((fn) => fn.mockReset());
    spectator = createComponent();
  });

  it('selects the console from the address and clears an earlier review', () => {
    spectator.detectChanges();

    expect(resetBulk).toHaveBeenCalled();
    expect(selectConsole).toHaveBeenCalledWith('dock-id');
    expect(spectator.element).toHaveText('Switch dock');
  });

  it('sends the pasted text for review', () => {
    spectator.typeInElement('Celeste\nHades', '[data-testid="bulk-text"]');
    spectator.click('[data-testid="bulk-review"]');

    expect(previewBulk).toHaveBeenCalledWith('Celeste\nHades');
  });

  it('shows what matched and what did not, and adds only the ticked lines', () => {
    lines.set([
      { line: 'Mario Kart 8 Deluxe', status: 'MATCHED', match: aConsoleSearchResultMock() },
      { line: 'Homebrew', status: 'NO_MATCH', match: null },
      { line: 'Celeste', status: 'ALREADY_PRESENT', match: null },
    ]);
    spectator.detectChanges();

    const list = spectator.query('[data-testid="bulk-lines"]') as HTMLElement;
    expect(list).toHaveText('Matches “Mario Kart 8 Deluxe”');
    expect(list).toHaveText('it will be added by title');
    expect(list).toHaveText('Already on this console');
    expect(spectator.query('[data-testid="bulk-confirm"]')).toHaveText('Add 2 games');

    spectator.click('input[aria-label="Add Homebrew"]');
    expect(spectator.query('[data-testid="bulk-confirm"]')).toHaveText('Add 1 game');
    spectator.click('[data-testid="bulk-confirm"]');

    expect(confirmBulk).toHaveBeenCalledWith([{ line: 'Mario Kart 8 Deluxe', igdbGameId: 13427, title: null }]);
  });

  it('cannot tick a game that is already there', () => {
    lines.set([{ line: 'Celeste', status: 'ALREADY_PRESENT', match: null }]);
    spectator.detectChanges();

    expect((spectator.query('input[type="checkbox"]') as HTMLInputElement).disabled).toBe(true);
  });

  it('summarises what was added, skipped and failed', () => {
    summary.set({ added: [{ gameId: 'g1', title: 'Hades', linkedExisting: false }], skipped: ['Celeste'], failed: [{ line: 'Ghost', reason: 'IGDB does not know that game' }] });
    spectator.detectChanges();

    const text = spectator.query('[data-testid="bulk-summary"]') as HTMLElement;
    expect(text).toHaveText('1 added');
    expect(text).toHaveText('Already there: Celeste');
    expect(text).toHaveText('Ghost: IGDB does not know that game');
  });
});
