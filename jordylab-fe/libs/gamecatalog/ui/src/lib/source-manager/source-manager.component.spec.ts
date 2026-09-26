import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aScanSourceMock, ScanLibraryType, ScanSource, ScanSourceStore } from '@jordylab-fe/gamecatalog/api';
import { SourceManagerComponent } from './source-manager.component';

describe('SourceManagerComponent', () => {
  const sources = signal<ScanSource[]>([]);
  const loading = signal(true);
  const error = signal<string | null>(null);
  const togglingId = signal<string | null>(null);
  const downloading = signal<ScanLibraryType | null>(null);

  const toggle = vi.fn<ScanSourceStore['toggle']>();
  const downloadClient = vi.fn<ScanSourceStore['downloadClient']>();

  const storeMock = {
    sources: sources.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    togglingId: togglingId.asReadonly(),
    downloading: downloading.asReadonly(),
    toggle,
    downloadClient,
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
    toggle.mockReset();
    downloadClient.mockReset();
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

    expect(spectator.query('[data-testid="download-steam-client"]')).not.toBeNull();
    expect(spectator.query('[data-testid="download-emudeck-client"]')).not.toBeNull();
  });

  it('shows an empty state when no sources exist', () => {
    populate([]);

    expect(spectator.element).toHaveText('No scan sources announced yet.');
  });

  it('shows an error state when loading fails', () => {
    loading.set(false);
    error.set('Failed to load scan sources.');
    spectator.detectChanges();

    expect(spectator.query('.text-destructive')).toHaveText('Failed to load scan sources.');
  });

  it('forwards the source to the store when its toggle is clicked', () => {
    populate([aScanSourceMock()]);

    spectator.click(spectator.query('button[role="switch"]') as HTMLElement);

    expect(toggle).toHaveBeenCalledWith(aScanSourceMock());
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
      anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
      populate([]);
    });

    afterEach(() => {
      anchorClick.mockRestore();
      Reflect.deleteProperty(URL, 'createObjectURL');
      Reflect.deleteProperty(URL, 'revokeObjectURL');
    });

    it('requests the Steam client from the store', () => {
      spectator.click('[data-testid="download-steam-client"]');

      expect(downloadClient).toHaveBeenCalledWith('steam', expect.any(Function));
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
});
