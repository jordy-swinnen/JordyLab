import {
  createServiceFactory,
  SpectatorService,
} from '@ngneat/spectator/vitest';
import { of, throwError } from 'rxjs';
import { GameCatalogApiService } from './gamecatalog-api.service';
import { aSwitchBulkLineMock } from './mocks/switch-bulk-line.model.mock';
import { aSwitchSearchResultMock } from './mocks/switch-search-result.model.mock';
import { SwitchBulkStore } from './switch-bulk.store';

describe('SwitchBulkStore', () => {
  let spectator: SpectatorService<SwitchBulkStore>;
  const previewSwitchBulk = vi.fn<GameCatalogApiService['previewSwitchBulk']>();
  const confirmSwitchBulk = vi.fn<GameCatalogApiService['confirmSwitchBulk']>();

  const createService = createServiceFactory({
    service: SwitchBulkStore,
    providers: [
      {
        provide: GameCatalogApiService,
        useValue: { previewSwitchBulk, confirmSwitchBulk },
      },
    ],
  });

  const pikmin = aSwitchBulkLineMock({
    line: 'Pikmin 4',
    candidates: [
      aSwitchSearchResultMock({ igdbGameId: 111, title: 'Pikmin 4' }),
    ],
  });
  const indie = aSwitchBulkLineMock({
    line: 'Some Indie Gem',
    status: 'NO_MATCH',
    candidates: [],
    include: false,
  });

  beforeEach(() => {
    previewSwitchBulk.mockReset();
    confirmSwitchBulk.mockReset();
    spectator = createService();
  });

  function previewed(): void {
    previewSwitchBulk.mockReturnValue(of({ lines: [pikmin, indie] }));
    spectator.service.setText('Pikmin 4\nSome Indie Gem');
    spectator.service.preview();
  }

  it('cannot preview an empty paste', () => {
    spectator.service.setText('   ');

    spectator.service.preview();

    expect(spectator.service.canPreview()).toBe(false);
    expect(previewSwitchBulk).not.toHaveBeenCalled();
  });

  it('turns the preview into editable rows that default to the best match and physical', () => {
    previewed();

    expect(previewSwitchBulk).toHaveBeenCalledWith('Pikmin 4\nSome Indie Gem');
    expect(spectator.service.rows()).toEqual([
      { ...pikmin, igdbGameId: 111, format: 'PHYSICAL' },
      { ...indie, igdbGameId: null, format: 'PHYSICAL' },
    ]);
    expect(spectator.service.includedCount()).toBe(1);
  });

  it('shows the server reason when the preview is refused', () => {
    previewSwitchBulk.mockReturnValue(
      throwError(() => ({ error: { detail: 'Paste at most 100 titles' } })),
    );
    spectator.service.setText('lots');

    spectator.service.preview();

    expect(spectator.service.error()).toBe('Paste at most 100 titles');
    expect(spectator.service.previewing()).toBe(false);
  });

  it('lets the admin tick, pick another match and set formats per line or for all', () => {
    previewed();

    spectator.service.toggleInclude(1);
    spectator.service.chooseMatch(0, null);
    spectator.service.setAllFormats('DIGITAL');
    spectator.service.setRowFormat(1, 'PHYSICAL');

    const [first, second] = spectator.service.rows();
    expect(first).toMatchObject({
      igdbGameId: null,
      format: 'DIGITAL',
      include: true,
    });
    expect(second).toMatchObject({ format: 'PHYSICAL', include: true });
  });

  it('confirms only ticked lines, by IGDB id or by title, then shows the summary', () => {
    previewed();
    spectator.service.toggleInclude(1);
    const summary = { added: [], alreadyPresent: ['Pikmin 4'], skipped: [] };
    confirmSwitchBulk.mockReturnValue(of(summary));

    spectator.service.confirm();

    expect(confirmSwitchBulk).toHaveBeenCalledWith([
      { line: 'Pikmin 4', igdbGameId: 111, title: null, format: 'PHYSICAL' },
      {
        line: 'Some Indie Gem',
        igdbGameId: null,
        title: 'Some Indie Gem',
        format: 'PHYSICAL',
      },
    ]);
    expect(spectator.service.summary()).toEqual(summary);
    expect(spectator.service.rows()).toEqual([]);
  });

  it('cannot confirm when nothing is ticked', () => {
    previewed();
    spectator.service.toggleInclude(0);

    spectator.service.confirm();

    expect(spectator.service.canConfirm()).toBe(false);
    expect(confirmSwitchBulk).not.toHaveBeenCalled();
  });

  it('keeps the rows when the confirm fails', () => {
    previewed();
    confirmSwitchBulk.mockReturnValue(throwError(() => ({ status: 500 })));

    spectator.service.confirm();

    expect(spectator.service.error()).toBe(
      'Something went wrong. Please try again.',
    );
    expect(spectator.service.rows()).toHaveLength(2);
    expect(spectator.service.confirming()).toBe(false);
  });
});
