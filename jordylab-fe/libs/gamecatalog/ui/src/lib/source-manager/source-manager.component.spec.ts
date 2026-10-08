import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import {
  aLibraryHealthMock,
  aLibraryStatusMock,
  aLibrarySyncRunMock,
  aScanSourceMock,
  HealthExceptions,
  HealthKind,
  LibraryHealth,
  LibraryStatus,
  LibrarySyncRun,
  ScanLibraryType,
  ScanSource,
  RefreshRun,
  RefreshRunKind,
  RefreshRunStore,
  aRefreshRunMock,
  ScanSourceStore,
} from '@jordylab-fe/gamecatalog/api';
import { SourceManagerComponent } from './source-manager.component';

// Built at run time: a literal JWT-shaped string is what the secret scan is there to catch.
const A_TOKEN = ['eyJfake', 'x'.repeat(24), 'y'.repeat(24)].join('.');

describe('SourceManagerComponent', () => {
  const sources = signal<ScanSource[]>([]);
  const health = signal<LibraryHealth | null>(null);
  const healthExceptions = signal<HealthExceptions | null>(null);
  const loadingExceptions = signal<HealthKind | null>(null);
  const showHealthExceptions = vi.fn<ScanSourceStore['showHealthExceptions']>();
  const hideHealthExceptions = vi.fn<ScanSourceStore['hideHealthExceptions']>();
  const loading = signal(true);
  const error = signal<string | null>(null);
  const togglingId = signal<string | null>(null);
  const editingSourceId = signal<string | null>(null);
  const renamingHostId = signal<string | null>(null);
  const hostNameProblem = signal<string | null>(null);
  const downloading = signal<ScanLibraryType | null>(null);
  const libraryStatus = signal<LibraryStatus | null>(null);
  const librarySyncing = signal<'OWNED' | 'FAMILY' | null>(null);
  const lastLibraryRun = signal<LibrarySyncRun | null>(null);

  const requestToggle = vi.fn<ScanSourceStore['requestToggle']>();
  const confirmDisable = vi.fn<ScanSourceStore['confirmDisable']>();
  const cancelDisable = vi.fn<ScanSourceStore['cancelDisable']>();
  const disableQuote = signal<ScanSourceStore['disableQuote'] extends () => infer T ? T : never>(null);
  const startEditingHostName = vi.fn<ScanSourceStore['startEditingHostName']>();
  const stopEditingHostName = vi.fn<ScanSourceStore['stopEditingHostName']>();
  const renameHost = vi.fn<ScanSourceStore['renameHost']>();
  const downloadClient = vi.fn<ScanSourceStore['downloadClient']>();
  const syncOwnedLibrary = vi.fn<ScanSourceStore['syncOwnedLibrary']>();
  const syncFamilyLibrary = vi.fn<ScanSourceStore['syncFamilyLibrary']>();

  const storeMock = {
    sources: sources.asReadonly(),
    health: health.asReadonly(),
    healthExceptions: healthExceptions.asReadonly(),
    loadingExceptions: loadingExceptions.asReadonly(),
    showHealthExceptions,
    hideHealthExceptions,
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    togglingId: togglingId.asReadonly(),
    editingSourceId: editingSourceId.asReadonly(),
    renamingHostId: renamingHostId.asReadonly(),
    hostNameProblem: hostNameProblem.asReadonly(),
    downloading: downloading.asReadonly(),
    libraryStatus: libraryStatus.asReadonly(),
    librarySyncing: librarySyncing.asReadonly(),
    lastLibraryRun: lastLibraryRun.asReadonly(),
    requestToggle,
    confirmDisable,
    cancelDisable,
    disableQuote: disableQuote.asReadonly(),
    startEditingHostName,
    stopEditingHostName,
    renameHost,
    downloadClient,
    syncOwnedLibrary,
    syncFamilyLibrary,
  };

  const dataRun = signal<RefreshRun | null>(null);
  const aiRun = signal<RefreshRun | null>(null);
  const refreshStarting = signal<RefreshRunKind | null>(null);
  const aiCostQuote = signal<number | null>(null);
  const refreshError = signal<string | null>(null);
  const startData = vi.fn<RefreshRunStore['startData']>();
  const requestAi = vi.fn<RefreshRunStore['requestAi']>();
  const confirmAi = vi.fn<RefreshRunStore['confirmAi']>();
  const cancelAi = vi.fn<RefreshRunStore['cancelAi']>();
  const stopRun = vi.fn<RefreshRunStore['stop']>();
  const dismissRefreshError = vi.fn<RefreshRunStore['dismissError']>();
  const refreshRunStoreMock = {
    dataRun: dataRun.asReadonly(),
    aiRun: aiRun.asReadonly(),
    starting: refreshStarting.asReadonly(),
    aiCostQuote: aiCostQuote.asReadonly(),
    error: refreshError.asReadonly(),
    startData,
    requestAi,
    confirmAi,
    cancelAi,
    stop: stopRun,
    dismissError: dismissRefreshError,
  };

  let spectator: Spectator<SourceManagerComponent>;

  const createComponent = createComponentFactory({
    component: SourceManagerComponent,
    providers: [
      provideRouter([]),
      { provide: ScanSourceStore, useValue: storeMock },
      { provide: RefreshRunStore, useValue: refreshRunStoreMock },
    ],
  });

  beforeEach(() => {
    sources.set([]);
    health.set(null);
    healthExceptions.set(null);
    loadingExceptions.set(null);
    showHealthExceptions.mockReset();
    hideHealthExceptions.mockReset();
    loading.set(true);
    error.set(null);
    togglingId.set(null);
    editingSourceId.set(null);
    renamingHostId.set(null);
    hostNameProblem.set(null);
    startEditingHostName.mockReset();
    stopEditingHostName.mockReset();
    renameHost.mockReset();
    downloading.set(null);
    libraryStatus.set(null);
    librarySyncing.set(null);
    lastLibraryRun.set(null);
    requestToggle.mockReset();
    confirmDisable.mockReset();
    cancelDisable.mockReset();
    disableQuote.set(null);
    dataRun.set(null);
    aiRun.set(null);
    refreshStarting.set(null);
    aiCostQuote.set(null);
    refreshError.set(null);
    [startData, requestAi, confirmAi, cancelAi, stopRun, dismissRefreshError].forEach((mock) => mock.mockReset());
    downloadClient.mockReset();
    syncOwnedLibrary.mockReset();
    syncFamilyLibrary.mockReset();
    spectator = createComponent();
  });

  const populate = (list: ScanSource[]) => {
    loading.set(false);
    sources.set(list);
    spectator.detectChanges();
  };

  it('shows skeletons while loading', () => {
    expect(spectator.queryAll('hlm-skeleton').length).toBeGreaterThan(0);
  });

  it('renders source hostname, type, counts and last outcome', () => {
    populate([aScanSourceMock()]);

    expect(spectator.query('[data-testid="source-label"]')).toHaveText('jordybox');
    expect(spectator.element).toHaveText('hostname: jordybox');
    expect(spectator.element).toHaveText('Steam library');
    expect(spectator.element).toHaveText('412 installed');
    expect(spectator.element).toHaveText('APPLIED');
  });

  describe('naming a host', () => {
    const named = aScanSourceMock({ displayName: 'Living room PC', label: 'Living room PC' });

    it('shows the name the admin chose first and the hostname as secondary text', () => {
      populate([named]);

      expect(spectator.query('[data-testid="source-label"]')).toHaveText('Living room PC');
      expect(spectator.element).toHaveText('hostname: jordybox');
    });

    it('offers "Give a name" for an unnamed host and "Rename" for a named one', () => {
      populate([aScanSourceMock(), { ...named, id: 'named-source' }]);

      const actions = spectator.queryAll('button[aria-label^="Rename"]').map((button) => button.textContent?.trim());
      expect(actions).toEqual(['Give a name', 'Rename']);
    });

    it('opens the editor for the clicked source', () => {
      populate([aScanSourceMock()]);

      spectator.click('button[aria-label="Rename jordybox"]');

      expect(startEditingHostName).toHaveBeenCalledWith(aScanSourceMock().id);
    });

    it('saves the typed name for the host', () => {
      editingSourceId.set(named.id);
      populate([named]);

      spectator.typeInElement('Basement PC', '#host-name-input');
      spectator.click('[data-testid="host-name-save"]');

      expect(renameHost).toHaveBeenCalledWith(named.hostId, 'Basement PC');
    });

    it('closes the editor on Cancel without saving', () => {
      editingSourceId.set(named.id);
      populate([named]);

      spectator.click('[data-testid="host-name-cancel"]');

      expect(stopEditingHostName).toHaveBeenCalled();
      expect(renameHost).not.toHaveBeenCalled();
    });

    it('lets the admin go back to the hostname with one click', () => {
      editingSourceId.set(named.id);
      populate([named]);

      spectator.click('[data-testid="host-name-clear"]');

      expect(renameHost).toHaveBeenCalledWith(named.hostId, '');
    });

    it('shows why a name was refused', () => {
      editingSourceId.set(named.id);
      hostNameProblem.set('That name is already used by another host or console.');
      populate([named]);

      expect(spectator.query('[data-testid="host-name-problem"]')).toHaveText('already used');
      expect(spectator.query('#host-name-input')?.getAttribute('aria-invalid')).toBe('true');
    });

    it('does not offer "Use hostname" when no name is set', () => {
      editingSourceId.set(aScanSourceMock().id);
      populate([aScanSourceMock()]);

      expect(spectator.query('[data-testid="host-name-clear"]')).toBeNull();
    });
  });

  describe('refresh everything', () => {
    beforeEach(() => {
      HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
        this.setAttribute('open', '');
      };
      HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
        this.removeAttribute('open');
      };
    });

    it('offers the two buttons and starts the free data refresh straight away', () => {
      populate([]);

      spectator.click('[data-testid="refresh-start-DATA"]');

      expect(spectator.query('[data-testid="refresh-start-DATA"]')).toHaveText('Refresh game data');
      expect(spectator.query('[data-testid="refresh-start-AI"]')).toHaveText('Regenerate AI data');
      expect(startData).toHaveBeenCalled();
    });

    it('asks the store for the AI run, which opens the cost confirmation instead of starting', () => {
      populate([]);

      spectator.click('[data-testid="refresh-start-AI"]');

      expect(requestAi).toHaveBeenCalled();
      expect(confirmAi).not.toHaveBeenCalled();
    });

    it('warns that the AI run costs money and names how many games, then confirms or cancels', () => {
      aiCostQuote.set(228);
      populate([]);

      const dialog = spectator.query('[data-testid="confirm-dialog"]');
      expect(dialog?.hasAttribute('open')).toBe(true);
      expect(dialog).toHaveText('228');
      expect(dialog).toHaveText('costs real money');

      spectator.click('[data-testid="confirm-dialog-confirm"]');
      spectator.click('[data-testid="confirm-dialog-cancel"]');

      expect(confirmAi).toHaveBeenCalled();
      expect(cancelAi).toHaveBeenCalled();
    });

    it('shows progress and a Stop button while a run is running, and no start button', () => {
      dataRun.set(aRefreshRunMock({ processed: 57, total: 228, failed: 1 }));
      populate([]);

      const card = spectator.query('[data-testid="refresh-card-DATA"]');
      expect(card?.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('25');
      expect(card).toHaveText('57 of 228 games');
      expect(card).toHaveText('1 failed');
      expect(spectator.query('[data-testid="refresh-start-DATA"]')).toBeNull();

      spectator.click('[data-testid="refresh-stop-DATA"]');

      expect(stopRun).toHaveBeenCalledWith(dataRun());
    });

    it('says what a finished run did, including why it ended early', () => {
      dataRun.set(aRefreshRunMock({ status: 'SUCCEEDED', processed: 228, total: 228, failed: 3 }));
      aiRun.set(
        aRefreshRunMock({
          kind: 'AI',
          status: 'FAILED',
          processed: 5,
          failed: 5,
          failureSummary: 'Stopped after 5 failures in a row: INSUFFICIENT_CREDITS',
        }),
      );
      populate([]);

      expect(spectator.query('[data-testid="refresh-card-DATA"] [data-testid="refresh-outcome"]')).toHaveText(
        '3 could not be refreshed',
      );
      expect(spectator.query('[data-testid="refresh-card-AI"] [data-testid="refresh-outcome"]')).toHaveText(
        'INSUFFICIENT_CREDITS',
      );
    });

    it('shows a refresh problem and lets the admin dismiss it', () => {
      refreshError.set('A refresh of this kind is already running.');
      populate([]);

      expect(spectator.query('[data-testid="refresh-error"]')).toHaveText('already running');

      spectator.click('[data-testid="refresh-error"] button');

      expect(dismissRefreshError).toHaveBeenCalled();
    });

    it('disables both start buttons while one is being sent', () => {
      refreshStarting.set('DATA');
      populate([]);

      expect(spectator.query<HTMLButtonElement>('[data-testid="refresh-start-AI"]')?.disabled).toBe(true);
      expect(spectator.query('[data-testid="refresh-start-DATA"]')).toHaveText('Starting…');
    });
  });

  it('exposes Steam and EmuDeck download buttons', () => {
    populate([]);

    expect(
      spectator.query('[data-testid="download-steam-client"]'),
    ).not.toBeNull();
    expect(
      spectator.query('[data-testid="download-emudeck-client"]'),
    ).not.toBeNull();
  });

  it('shows an empty state when no sources exist', () => {
    populate([]);

    expect(spectator.element).toHaveText('No scan sources announced yet.');
  });

  it('shows an error state when loading fails', () => {
    loading.set(false);
    error.set('Failed to load scan sources.');
    spectator.detectChanges();

    expect(spectator.query('.text-destructive')).toHaveText(
      'Failed to load scan sources.',
    );
  });

  it('asks the store to toggle the source when its switch is clicked', () => {
    populate([aScanSourceMock()]);

    spectator.click(spectator.query('button[role="switch"]') as HTMLElement);

    expect(requestToggle).toHaveBeenCalledWith(aScanSourceMock());
  });

  describe('turning a source off', () => {
    beforeEach(() => {
      HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
        this.setAttribute('open', '');
      };
      HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
        this.removeAttribute('open');
      };
    });

    const quote = (hiddenGames: number, stillVisibleElsewhere: number) => ({
      source: aScanSourceMock({ label: 'Living room PC' }),
      impact: { hiddenGames, stillVisibleElsewhere },
    });

    it('says how many games will disappear and that nothing is deleted', () => {
      disableQuote.set(quote(87, 5));
      populate([aScanSourceMock()]);

      const dialog = spectator.query('lib-confirm-dialog[heading="Turn this source off?"] [data-testid="confirm-dialog"]');
      expect(dialog?.hasAttribute('open')).toBe(true);
      expect(dialog).toHaveText('87');
      expect(dialog).toHaveText('Living room PC');
      expect(dialog).toHaveText('5 other games stay visible');
      expect(dialog).toHaveText('Nothing is deleted');
    });

    it('says plainly when no game will disappear', () => {
      disableQuote.set(quote(0, 12));
      populate([aScanSourceMock()]);

      expect(spectator.query('lib-confirm-dialog[heading="Turn this source off?"]')).toHaveText('No game will disappear');
    });

    it('still asks, without a number, when the number could not be fetched', () => {
      disableQuote.set(quote(-1, -1));
      populate([aScanSourceMock()]);

      expect(spectator.query('lib-confirm-dialog[heading="Turn this source off?"]')).toHaveText('will no longer show');
    });

    it('confirms or cancels through the store', () => {
      disableQuote.set(quote(3, 0));
      populate([aScanSourceMock()]);

      spectator.click('lib-confirm-dialog[heading="Turn this source off?"] [data-testid="confirm-dialog-confirm"]');
      spectator.click('lib-confirm-dialog[heading="Turn this source off?"] [data-testid="confirm-dialog-cancel"]');

      expect(confirmDisable).toHaveBeenCalled();
      expect(cancelDisable).toHaveBeenCalled();
    });
  });

  it('no longer offers a "Refresh pending data" button', () => {
    populate([]);

    expect(spectator.query('[data-testid="refresh-pending-data"]')).toBeNull();
    expect(spectator.element).not.toHaveText('Refresh pending data');
  });

  describe('download', () => {
    const createObjectURL = vi.fn(() => 'blob:script');
    const revokeObjectURL = vi.fn();
    let anchorClick: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
      // jsdom does not implement the object-URL API
      Object.assign(URL, { createObjectURL, revokeObjectURL });
      createObjectURL.mockClear();
      revokeObjectURL.mockClear();
      anchorClick = vi
        .spyOn(HTMLAnchorElement.prototype, 'click')
        .mockImplementation(() => undefined);
      populate([]);
    });

    afterEach(() => {
      anchorClick.mockRestore();
      Reflect.deleteProperty(URL, 'createObjectURL');
      Reflect.deleteProperty(URL, 'revokeObjectURL');
    });

    it('requests the Steam client from the store', () => {
      spectator.click('[data-testid="download-steam-client"]');

      expect(downloadClient).toHaveBeenCalledWith(
        'steam',
        expect.any(Function),
      );
    });

    it('saves the generated client as a file named after the library', () => {
      spectator.click('[data-testid="download-emudeck-client"]');
      const onReady = downloadClient.mock.calls[0][1];

      onReady(new Blob(['print("hi")']));

      const link = anchorClick.mock.contexts[0] as HTMLAnchorElement;
      expect(link.download).toBe('jordylab-scan-emudeck.py');
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:script');
    });
  });

  describe('library', () => {
    it('asks the store to sync the owned library', () => {
      populate([aScanSourceMock()]);

      spectator.click('[data-testid="sync-owned-library"]');

      expect(syncOwnedLibrary).toHaveBeenCalled();
    });

    it('sends the pasted family token to the store', () => {
      populate([aScanSourceMock()]);

      spectator.typeInElement(A_TOKEN, 'input[type="password"]');
      spectator.click('[data-testid="sync-family-library"]');

      expect(syncFamilyLibrary).toHaveBeenCalledWith(A_TOKEN);
    });

    it('picks the token out of the whole Steam config page when that is what was pasted', () => {
      populate([aScanSourceMock()]);

      spectator.typeInElement(
        JSON.stringify({ success: 1, data: { webapi_token: A_TOKEN } }),
        'input[type="password"]',
      );
      spectator.click('[data-testid="sync-family-library"]');

      expect(syncFamilyLibrary).toHaveBeenCalledWith(A_TOKEN);
    });

    it('explains an empty Steam page instead of sending it', () => {
      populate([aScanSourceMock()]);

      spectator.typeInElement('{"success":1,"data":[]}', 'input[type="password"]');
      spectator.click('[data-testid="sync-family-library"]');

      expect(syncFamilyLibrary).not.toHaveBeenCalled();
      expect(spectator.query('[data-testid="family-token-problem"]')).toHaveText(
        'not signed in to the Steam store',
      );
    });

    it('asks for a token when nothing was pasted', () => {
      populate([aScanSourceMock()]);

      spectator.click('[data-testid="sync-family-library"]');

      expect(syncFamilyLibrary).not.toHaveBeenCalled();
      expect(spectator.query('[data-testid="family-token-problem"]')).toHaveText('Paste your Steam token first');
    });

    it('says in a sentence that a sync which changed nothing worked', () => {
      populate([aScanSourceMock()]);
      lastLibraryRun.set(aLibrarySyncRunMock({ outcome: 'NO_CHANGE' }));
      spectator.detectChanges();

      expect(spectator.query('[data-testid="library-run-summary"]')).toHaveText('already up to date');
    });

    it('clears the pasted family token from the input once it is sent', () => {
      populate([aScanSourceMock()]);

      spectator.typeInElement(A_TOKEN, 'input[type="password"]');
      spectator.click('[data-testid="sync-family-library"]');

      const input = spectator.query(
        'input[type="password"]',
      ) as HTMLInputElement;
      expect(input.value).toBe('');
    });

    it('disables the owned sync and shows a hint when Steam is not configured', () => {
      populate([aScanSourceMock()]);
      libraryStatus.set(aLibraryStatusMock({ ownedConfigured: false }));
      spectator.detectChanges();

      const button = spectator.query('[data-testid="sync-owned-library"]');
      expect(button?.hasAttribute('disabled')).toBe(true);
      expect(spectator.element).toHaveText('Steam account not configured');
    });
  });

  describe('library health', () => {
    it('shows how many games lack a cover, a description and a search entry, with percentages', () => {
      health.set(aLibraryHealthMock({ totalGames: 200, gamesWithoutCover: 10, gamesWithoutDescription: 20, gamesPendingIndex: 0 }));
      populate([]);

      expect(spectator.query('[data-testid="health-count-COVER"]')).toHaveText('10');
      expect(spectator.query('[data-testid="health-count-COVER"]')).toHaveText('5 %');
      expect(spectator.query('[data-testid="health-count-DESCRIPTION"]')).toHaveText('10 %');
      expect(spectator.query('[data-testid="show-exceptions-INDEX"]')).toBeNull();
    });

    it('asks for the games behind a count and lists them with links', () => {
      health.set(aLibraryHealthMock());
      populate([]);

      spectator.click('[data-testid="show-exceptions-COVER"]');
      expect(showHealthExceptions).toHaveBeenCalledWith('COVER');

      healthExceptions.set({ kind: 'COVER', total: 12, games: [{ id: 'game-1', title: 'Obscure Game' }] });
      spectator.detectChanges();
      const link = spectator.query('[data-testid="health-exceptions"] a') as HTMLAnchorElement;
      expect(link).toHaveText('Obscure Game');
      expect(link.getAttribute('href')).toBe('/games/game-1');

      spectator.click('[data-testid="health-exceptions"] button');
      expect(hideHealthExceptions).toHaveBeenCalled();
    });

    it('shows no health panel until the numbers arrive', () => {
      populate([]);

      expect(spectator.query('[data-testid="library-health"]')).toBeNull();
    });
  });
});
