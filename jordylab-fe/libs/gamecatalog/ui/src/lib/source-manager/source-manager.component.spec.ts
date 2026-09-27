import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import {
  aLibraryStatusMock,
  aScanSourceMock,
  LibraryStatus,
  LibrarySyncRun,
  ScanLibraryType,
  ScanSource,
  ScanSourceStore,
} from '@jordylab-fe/gamecatalog/api';
import { SourceManagerComponent } from './source-manager.component';

describe('SourceManagerComponent', () => {
  const sources = signal<ScanSource[]>([]);
  const loading = signal(true);
  const error = signal<string | null>(null);
  const togglingId = signal<string | null>(null);
  const downloading = signal<ScanLibraryType | null>(null);
  const refreshingPending = signal(false);
  const refreshProgress = signal<string | null>(null);
  const libraryStatus = signal<LibraryStatus | null>(null);
  const librarySyncing = signal<'OWNED' | 'FAMILY' | null>(null);
  const lastLibraryRun = signal<LibrarySyncRun | null>(null);

  const toggle = vi.fn<ScanSourceStore['toggle']>();
  const downloadClient = vi.fn<ScanSourceStore['downloadClient']>();
  const refreshPending = vi.fn<ScanSourceStore['refreshPending']>();
  const syncOwnedLibrary = vi.fn<ScanSourceStore['syncOwnedLibrary']>();
  const syncFamilyLibrary = vi.fn<ScanSourceStore['syncFamilyLibrary']>();

  const storeMock = {
    sources: sources.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    togglingId: togglingId.asReadonly(),
    downloading: downloading.asReadonly(),
    refreshingPending: refreshingPending.asReadonly(),
    refreshProgress: refreshProgress.asReadonly(),
    libraryStatus: libraryStatus.asReadonly(),
    librarySyncing: librarySyncing.asReadonly(),
    lastLibraryRun: lastLibraryRun.asReadonly(),
    toggle,
    downloadClient,
    refreshPending,
    syncOwnedLibrary,
    syncFamilyLibrary,
  };

  let spectator: Spectator<SourceManagerComponent>;

  const createComponent = createComponentFactory({
    component: SourceManagerComponent,
    providers: [{ provide: ScanSourceStore, useValue: storeMock }],
  });

  beforeEach(() => {
    sources.set([]);
    loading.set(true);
    error.set(null);
    togglingId.set(null);
    downloading.set(null);
    refreshingPending.set(false);
    refreshProgress.set(null);
    libraryStatus.set(null);
    librarySyncing.set(null);
    lastLibraryRun.set(null);
    toggle.mockReset();
    downloadClient.mockReset();
    refreshPending.mockReset();
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

    expect(spectator.element).toHaveText('jordybox:STEAM');
    expect(spectator.element).toHaveText('hostname: jordybox');
    expect(spectator.element).toHaveText('Steam library');
    expect(spectator.element).toHaveText('412 installed');
    expect(spectator.element).toHaveText('APPLIED');
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

  it('forwards the source to the store when its toggle is clicked', () => {
    populate([aScanSourceMock()]);

    spectator.click(spectator.query('button[role="switch"]') as HTMLElement);

    expect(toggle).toHaveBeenCalledWith(aScanSourceMock());
  });

  it('asks the store to refresh pending data and shows progress', () => {
    populate([aScanSourceMock()]);

    spectator.click('[data-testid="refresh-pending-data"]');

    expect(refreshPending).toHaveBeenCalled();

    refreshingPending.set(true);
    refreshProgress.set('Refreshing… 4 left');
    spectator.detectChanges();

    expect(spectator.element).toHaveText('Refreshing… 4 left');
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

      spectator.typeInElement('family-token', 'input[type="password"]');
      spectator.click('[data-testid="sync-family-library"]');

      expect(syncFamilyLibrary).toHaveBeenCalledWith('family-token');
    });

    it('clears the pasted family token from the input once it is sent', () => {
      populate([aScanSourceMock()]);

      spectator.typeInElement('family-token', 'input[type="password"]');
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
});
