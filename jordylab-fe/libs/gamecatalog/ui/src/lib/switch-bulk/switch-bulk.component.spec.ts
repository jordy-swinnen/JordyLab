import { computed, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import {
  aSwitchBulkLineMock,
  aSwitchSearchResultMock,
  SwitchBulkRow,
  SwitchBulkStore,
  SwitchBulkSummary,
} from '@jordylab-fe/gamecatalog/api';
import { SwitchBulkComponent } from './switch-bulk.component';

describe('SwitchBulkComponent', () => {
  const text = signal('');
  const rows = signal<SwitchBulkRow[]>([]);
  const previewing = signal(false);
  const confirming = signal(false);
  const summary = signal<SwitchBulkSummary | null>(null);
  const error = signal<string | null>(null);
  const setText = vi.fn<SwitchBulkStore['setText']>();
  const preview = vi.fn<SwitchBulkStore['preview']>();
  const toggleInclude = vi.fn<SwitchBulkStore['toggleInclude']>();
  const chooseMatch = vi.fn<SwitchBulkStore['chooseMatch']>();
  const setRowFormat = vi.fn<SwitchBulkStore['setRowFormat']>();
  const setAllFormats = vi.fn<SwitchBulkStore['setAllFormats']>();
  const confirm = vi.fn<SwitchBulkStore['confirm']>();

  const storeMock = {
    text: text.asReadonly(),
    rows: rows.asReadonly(),
    previewing: previewing.asReadonly(),
    confirming: confirming.asReadonly(),
    summary: summary.asReadonly(),
    error: error.asReadonly(),
    includedCount: computed(() => rows().filter((row) => row.include).length),
    canPreview: computed(() => text().trim().length > 0),
    canConfirm: computed(() => rows().some((row) => row.include)),
    setText,
    preview,
    toggleInclude,
    chooseMatch,
    setRowFormat,
    setAllFormats,
    confirm,
    reset: vi.fn<SwitchBulkStore['reset']>(),
  };

  let spectator: Spectator<SwitchBulkComponent>;
  const createComponent = createComponentFactory({
    component: SwitchBulkComponent,
    providers: [provideRouter([]), { provide: SwitchBulkStore, useValue: storeMock }],
  });

  const pikminRow: SwitchBulkRow = {
    ...aSwitchBulkLineMock({
      line: 'Pikmin 4',
      candidates: [
        aSwitchSearchResultMock({ igdbGameId: 111, title: 'Pikmin 4' }),
        aSwitchSearchResultMock({ igdbGameId: 112, title: 'Pikmin 3 Deluxe' }),
      ],
    }),
    igdbGameId: 111,
    format: 'PHYSICAL',
  };
  const presentRow: SwitchBulkRow = {
    ...aSwitchBulkLineMock({ line: 'Splatoon 3', status: 'ALREADY_PRESENT', include: false }),
    igdbGameId: 111,
    format: 'PHYSICAL',
  };

  function select(selector: string): HTMLSelectElement {
    const element = spectator.query<HTMLSelectElement>(selector);
    if (!element) {
      throw new Error(`No select matches ${selector}`);
    }

    return element;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    text.set('');
    rows.set([]);
    summary.set(null);
    error.set(null);
    spectator = createComponent();
  });

  it('previews the pasted list', () => {
    spectator.typeInElement('Pikmin 4', '#switch-bulk-text');
    text.set('Pikmin 4');
    spectator.detectChanges();

    spectator.click('[data-testid="bulk-preview"]');

    expect(setText).toHaveBeenCalledWith('Pikmin 4');
    expect(preview).toHaveBeenCalled();
  });

  it('shows one review row per line with its status, and locks lines already in the catalog', () => {
    rows.set([pikminRow, presentRow]);
    spectator.detectChanges();

    const rendered = spectator.queryAll('[data-testid="bulk-row"]');
    expect(rendered).toHaveLength(2);
    expect(rendered[0]).toHaveText('Match');
    expect(rendered[1]).toHaveText('Already in catalog');
    expect(spectator.query<HTMLInputElement>('[aria-label="Add Splatoon 3"]')?.disabled).toBe(true);
    expect(spectator.query('[aria-label="Match for Splatoon 3"]')).toBeNull();
    expect(spectator.query('[data-testid="bulk-confirm"]')).toHaveText('Add 1 games');
  });

  it('forwards the review edits to the store', () => {
    rows.set([pikminRow]);
    spectator.detectChanges();

    spectator.click('[aria-label="Add Pikmin 4"]');
    spectator.selectOption(select('[aria-label="Match for Pikmin 4"]'), '112');
    spectator.selectOption(select('[aria-label="Match for Pikmin 4"]'), '');
    spectator.selectOption(select('[aria-label="Format for Pikmin 4"]'), 'DIGITAL');
    spectator.selectOption(select('#switch-bulk-all-format'), 'DIGITAL');

    expect(toggleInclude).toHaveBeenCalledWith(0);
    expect(chooseMatch).toHaveBeenNthCalledWith(1, 0, 112);
    expect(chooseMatch).toHaveBeenNthCalledWith(2, 0, null);
    expect(setRowFormat).toHaveBeenCalledWith(0, 'DIGITAL');
    expect(setAllFormats).toHaveBeenCalledWith('DIGITAL');
  });

  it('confirms and then shows the summary', () => {
    rows.set([pikminRow]);
    spectator.detectChanges();

    spectator.click('[data-testid="bulk-confirm"]');
    expect(confirm).toHaveBeenCalled();

    summary.set({ added: [], alreadyPresent: ['Splatoon 3'], skipped: [{ line: 'Gone', reason: 'IGDB game not found' }] });
    spectator.detectChanges();

    expect(spectator.query('[data-testid="bulk-summary"]')).toHaveText('already in catalog 1');
    expect(spectator.query('[data-testid="bulk-summary"]')).toHaveText('Gone — IGDB game not found');
  });

  it('shows the error from the store', () => {
    error.set('Paste at most 100 titles');
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Paste at most 100 titles');
  });
});
